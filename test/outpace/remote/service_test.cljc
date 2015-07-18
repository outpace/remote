(ns outpace.remote.service-test
  #?(:clj (:require [cemerick.cljs.test :refer [block-or-done]]
                    [clojure.core.async :as async :refer [go]]
                    [clojure.test :refer :all]
                    [outpace.remote.service :as service :refer [defendpoint
                                                                defservice
                                                                headers
                                                                endpoint
                                                                method
                                                                on-status
                                                                on-request]]))
  #?@(:cljs
      [(:require [cemerick.cljs.test]
                 [cljs.core.async :as async]
                 [cljs-http.client :as http]
                 [outpace.remote.service :as service])
       (:require-macros [cemerick.cljs.test :refer [is are deftest run-tests testing block-or-done]]
                        [cljs.core.async.macros :refer [go]]
                        [outpace.remote.service :refer [defendpoint
                                                        defservice
                                                        headers
                                                        endpoint
                                                        method
                                                        on-status
                                                        on-request]])]))

(defservice search-service
  (on-status
    (200 [body]
      body)
    (500 [_] nil))

  (endpoint :repos "https://api.github.com/users"
    (method :get)
    (on-request [req]
      (assoc req :with-credentials? false))))

;; We are testing a real enpoint because we had problems
;; doing with-redefs in ClojureScript :(
(deftest ^:async async-service-test
  (let [complete (async/chan)]
    (go (is (= 200
               (:status (async/<! (search-service :repos {})))))
        (async/>! complete true))
    (block-or-done complete)))

(deftest failing-on-request-test
  (testing "failing on-request handler"
    (let [spec {:method :get
                :url-template "https://api.github.com/users"
                :on-request
                (fn [req]
                  (assoc req :with-credentials? false)
                  (throw (ex-info "TESTS ARE DOCS!" {:fact "A test is not a doc."})))
                :on-status
                {200 identity
                 500 identity}}
          svc (service/make-endpoint spec)]
      (is (thrown? #?(:clj Exception :cljs js/Error) (svc {}))))))

(deftest ^:async async-failing-on-status-test
  (testing "failing on-status handler"
    (let [complete (async/chan 1)
          ex-chan (async/chan 1)
          spec {:method :get
                :url-template "https://api.github.com/users"
                :on-request
                (fn [req]
                  (assoc req :with-credentials? false))
                :on-exception
                (fn [ex]
                  (async/put! ex-chan ex))
                :on-status
                {200 (fn [resp]
                       (throw (ex-info "TESTS ARE DOCS!" {:fact "A test is not a doc."}))
                       resp)
                 500 identity}}
          svc (service/make-endpoint spec)]
      (go
        (svc {})
        (is (instance? #?(:clj Exception :cljs js/Error) (async/<! ex-chan)))
        (async/>! complete true))
      (block-or-done complete))))

(deftest template-test
  (testing "template function should work"
    (is (= "/user/4" ((service/template "/user/:user_id") {:user_id 4})))))
