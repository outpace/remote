# Remote

A Clojure(Script) library and DSL for building client to remote service
APIs.

## Usage

Add the following dependency to your `project.clj` file:

[![Clojars Project](http://clojars.org/com.outpace/remote/latest-version.svg)](http://clojars.org/com.outpace/remote)

## DSL

### Endpoint definitions

```clj
;; The url-template specifies that a map containing the key :id must
;; be passed to render the url.
(defendpoint find-user-by-id "/users/:id"
  ;; You *must* specify the HTTP request method for each endpoint. Remote
  ;; does not make assumptions or choose so-called "sane"
  ;; defaults.
  (method :get)

  ;; Set the default headers for each HTTP request made by this endpoint
  ;; function.
  (headers {"X-Foo" "bar"})

  ;; The on-request clause creates the function to be called upon each
  ;; request allowing you to transform it before it is sent. The arity
  ;; of this function must be at least 1 and the first argument will
  ;; always be the request map. Any additional arities in this signature
  ;; are the arguments following the template parameters argument (the
  ;; first) for the returned endpoint function.
  (on-request [req query-params]
    (assoc req :query-params query-params))

  ;; The on-status clause allows you to configure per-status response
  ;; handlers for each HTTP status code.
  (on-status
    ;; Here we transform the response value before placing it on the
    ;; output channel returned by making a request to this endpoint.
    (200 [{:keys [body] :as resp}]
      body)
    ;; Returning nil will automatically close the output channel.
    (500 [_] nil)))
```

This endpoint function can now be called with:

```clj
;; The first argument is the template parameters for the url
;; template. The second argument is the query-params argument from the
;; on-request handler we created.
(find-user-by-id {:id 1} {:keys "name,email"})
;; => cljs.core.async.impl.channels.ManyToManyChannel
```

### Service definitions

```clj
(defservice user-service
  (headers {"X-Foo" "bar"})

  ;; The on-status clause allows you to configure per-status response
  ;; handlers for each endpoint.
  (on-status
    ;; Always put the data we recieved on the output channel to avoid
    ;; repetitous code.
    (200 [{:keys [body]}]
      body)
    (403 [_]
      (navigate-to-authentication!)))

  ;; The endpoint clause allows you to configure service endpoints.
  (endpoint :all "/users"
    (method :get))

  (endpoint :search "/users/search"
    (method :get)
    ;; headers, on-request, and on-status work exactly like they do in
    ;; defendpoint. Here they are scoped to the :search endpoint. Map
    ;; values such as the ones given by headers and on-status are merged
    ;; with the default values specified in the root scope of the
    ;; defservice block.
    (headers {"X-Foo" nil ;; Remove the X-Foo header.
              "X-Bar" "baz"}) ;; Add the X-Bar header.
    (on-request [req query-params]
      (assoc req :query-params query-params))))
```

This service can now be called with:

```clj
;; Like the endpoint function except we pass the endpoint name as the
;; first argument.
(user-service :search {} {:name "bill"})
;; => cljs.core.async.impl.channels.ManyToManyChannel
```

## Releasing

1. Set vesion number in `project.clj` to be version you want released
with a `-SNAPSHOT` suffix.
1. Run `bin/release`.

## Contributors (alphabetical)

- [Alex Rutkowski](https://github.com/alexmarie)
- [Alex Verkhovsky](https://github.com/alexeyv)
- [Alexander Taggart](https://github.com/ataggart)
- [Carin Meier](https://github.com/gigasquid)
- [Case Nelson](https://github.com/snoe)
- [Creighton Kirkendall](https://github.com/ckirkendall)
- [Francisco Viramontes](https://github.com/kidpollo)
- [Joel Holdbrooks](https://github.com/noprompt)
- [Josh Headapohl](https://github.com/joshhead)

## License

Copyright © Outpace

Released under the Apache License, Version 2.0.
