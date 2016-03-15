# 0.4.3

- Upgraded dependencies (required for Clojure 1.8)
  see https://groups.google.com/forum/#!msg/clojure/LT7vDT6fWxA/ol_BC_Fbt7MJ

# 0.4.2

- Fixed a defservice bug in CLJS where requests with params were not passing
  params.

# 0.4.0

- Switch to using Clojure 1.7's reader conditionals instead of cljx.
- Bump Clojure dependency to 1.7.
- Bump ClojureScript dependency to 0.0-2268.
- Fixes bug where channel not always closed when finished.

# 0.3.1

- Initial public release.
