(ns outpace.remote.service
  #?(:clj (:require [clojure.walk :as walk]
                    [clojure.core.async :as async :refer [go]]
                    [clj-http.client :as http]
                    [clojure.string :as string]))
  #?@(:cljs
      [(:require [cljs-http.client :as http]
                 [cljs.core.async :as async]
                 [clojure.string :as string])
       (:require-macros
        [cljs.core.async.macros :refer [go]])]))


;; ---------------------------------------------------------------------
;; Utilities

#?(:clj
   (defn template
     "Return a string template fn from s."
     ([s]
      (template s {:regex #":([a-z_-]+)"}))
     ([s {:keys [regex]}]
      (fn [m]
        (string/replace s regex
                        (fn a-replace [match]
                          (let [k (keyword (last match))
                                v (get m k)]
                            (if v
                              (str v)
                              (throw (Exception. (str "Template variable " k " missing"))))))))))
   :cljs
   (defn template
     "Return a string template fn from s."
     ([s]
      (template s {:regex #":([a-z_-]+)"}))
     ([s {:keys [regex]}]
      (fn [m]
        (string/replace s regex
                        (fn a-replace [$0 $1]
                          (let [k (keyword $1)
                                v (get m k)]
                            (if v
                              (str v)
                              (throw (js/Error. (str "Template variable " $1 " missing")))))))))))

(def method->http-fn
  {:get http/get
   :put http/put
   :post http/post
   :delete http/delete
   :options http/options
   :patch http/patch
   :head http/head})

(defn service-spec?
  [spec]
  (contains? spec :endpoints))

(defn add-headers
  [spec headers]
  {:pre [(map? spec) (map? headers)]}
  (assoc spec :headers headers))

(defn add-method
  [spec method]
  {:pre [(map? spec) (keyword? method)]}
  (if (service-spec? spec)
    spec
    (assoc spec :method method)))

(defn add-on-request
  [spec f]
  {:pre [(map? spec) (ifn? f)]}
  (assoc spec :on-request f))

(defn add-on-status
  [spec m]
  {:pre [(map? spec) (map? m)]}
  (assoc spec :on-status m))

(defn add-on-exception
  [spec f]
  {:pre [(map? spec) (ifn? f)]}
  (assoc spec :on-exception f))

(defn add-endpoint
  [spec key endpoint-config]
  {:pre [(service-spec? spec)
         (keyword? key)
         (map? endpoint-config)]}
  (assoc-in spec [:endpoints key] endpoint-config))

#?(:clj
   (defn url-call [http-fn url req]
     (let [result-chan (async/chan)]
       (async/put! result-chan (http-fn url req))
       result-chan))
   :cljs
   (defn url-call [http-fn url req]
     (http-fn url req)))


;; ---------------------------------------------------------------------
;; Endpoint construction

(defn- valid-status-key?
  [key]
  (or (integer? key) (= :default key)))

(defn make-endpoint
  [{:keys [method
           url-template
           on-request
           on-status
           on-exception
           headers
           username
           password]
    :or {on-request identity
         on-status {}
         on-exception identity
         headers {}}
    :as opts}]
  (assert (method->http-fn method)
          (str ":method must be one of " (keys method->http-fn)))
  (assert (string? url-template)
          ":url-template must be a string")
  (assert (ifn? on-request)
          ":on-request is not a function")
  (assert (and (map? on-status)
               (every? (fn [[k v]]
                         (and (valid-status-key? k) (ifn? v)))
                       on-status))
          ":on-status is not a map of {integer ifn}")
  (let [http-fn (method->http-fn method)
        t (template url-template)]
    (fn a-endpoint [template-params & args]
      (let [url (t template-params)
            cljs-http-opts (-> opts
                               (assoc :headers headers)
                               (dissoc :method :url-template :on-request :on-status :on-exception))
            req (apply on-request cljs-http-opts args)
            out (async/chan 1)
            resp-ch (url-call http-fn url req)]
        (go
          (try
            (let [{:keys [status] :as resp} (async/<! resp-ch)
                  status-handler (or (get on-status status)
                                     (get on-status :default)
                                     identity)
                  v (status-handler resp)]
              (when v
                (async/put! out v)))
            (catch #?(:clj Exception :cljs js/Error) e
                   (on-exception e))
            (finally
              (async/close! out))))

        out))))

;; ---------------------------------------------------------------------
;; Service construction

(defn make-service
  "Return a function which takes an endpoint key and any number of
  additional arguments and applies the function given by
  (make-endpoint (get-in service-spec [:endpoints key])). If the
  service-spec includes configuration for :headers, :on-status, or
  :on-request, the endpoint configuration will be merged in to it.

  Options:
    * headers - A map of headers to use with each HTTP request.
    * on-status - A map of status code to response handler.
    * on-request - A function of at least one arity which accepts the
                   request map as it's first argument.
    * endpoints - A map of endpoint-key to endpoint-config. See
                  `make-endpoint` for endpoint-config details.
  "
  [{:keys [endpoints] :as service-spec}]
  {:pre [(service-spec? service-spec)
         (map? endpoints)
         (not (empty? endpoints))]}
  (fn a-service [endpoint & args]
    (if-let [endpoint-spec (get endpoints endpoint)]
      (let [spec (merge-with
                  (fn [svc ept]
                    (if (and (map? svc) (map? ept))
                      (merge svc ept)
                      ept))
                  (dissoc service-spec :endpoints)
                  endpoint-spec)
            f (make-endpoint spec)]
        (apply f args))
      (throw (#?(:clj Exception. :cljs js/Error.) (str "no endpoint spec for " (pr-str endpoint)))))))

;; ---------------------------------------------------------------------
;; Macros

#?(:clj
   (defmacro method
     "Shortand syntax to add :method configuration to an endpoint spec.

     Ex.
       (method {} :get)
  "
     [spec method]
     `(add-method ~spec ~(keyword method))))

#?(:clj
   (defmacro headers
     "Shortand syntax to add :headers configuration to an endpoint spec.

     Ex.
       (headers {} {\"X-Foo\" \"bar\"})
  "
     [spec headers]
     `(add-headers ~spec ~headers)))

#?(:clj
   (defmacro on-request
     "Shorthand syntax to add :on-request configuation to an endpoint or
      spec.

     Ex.
       (on-request {} [req]
         (assoc-in req [:headers \"X-Foo\"] \"bar\"))
  "
     [spec & fn-tail]
     `(add-on-request ~spec (fn ~@fn-tail))))

#?(:clj
   (defmacro
     ^{:arglists '([spec & status+fn-tail*])}
     on-status
     "Shorthand syntax to add :on-status configuation to an endpoint or
     service spec.

     Ex.
       (on-status {}
         (200 [_] (pritnln \"YAY!\"))
         (404 [_] (println \"BOO!\")))
  "
     [spec & fn-specs]
     (let [on-status-map
           (into {}
                 (map
                  (fn [[status & fn-tail]]
                    `[~status (fn ~@fn-tail)])
                  fn-specs))]
       `(add-on-status ~spec ~on-status-map))))

#?(:clj
   (defmacro on-exception
     "Shorthand syntax to add :on-exception configuation to an endpoint or
     spec.

     Ex.
       (on-exception {} [err]
         (println \"ERROR: \" (.getMessage err))
  "
     [spec & fn-tail]
     `(add-on-exception ~spec (fn ~@fn-tail))))

#?(:clj
   (defmacro endpoint
     "Shorthand syntax to add :on-response configuration to an endpoint
     or service spec.

     Ex.
       (endpoint {} \"/users/:id\"
         (method :get))
  "
     [service-spec key url-template & config]
     `(let [endpoint-spec# {:url-template ~url-template}]
        (add-endpoint ~service-spec ~key (-> endpoint-spec# ~@config)))))

#?(:clj
   (defmacro defendpoint
     "Shorthand syntax for defining and endpoint and configuring it.

     Ex.

       (defendpoint all \"/users\"
         (method :get)

         (on-status
           (200 [{:keys [body]}]
             (let [c (count (:body resp))]
               (js/console.log c \"USERS FOUND\")))))
  "
     [sym url-template & body]
     `(def ~sym
        (make-endpoint (-> {:url-template ~url-template} ~@body)))))

#?(:clj
   (defmacro defservice
     "Shorthand syntax for defining a service and configuring it.

     Ex.

       (defservice user-service
         (on-status
           (200 [_]
             (js/console.log \"SUCCESS\")))
         (endpoint :all \"/users\"
           (method :get)))
  "
     [sym & body]
     (let [spec (walk/macroexpand-all `(-> {:endpoints {}} ~@body))]
       `(def ~sym (make-service ~spec)))))
