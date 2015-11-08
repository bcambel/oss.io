(ns hsm.dev
  (:require [environ.core :refer [env]])
   (:use [clojure.tools.namespace.repl :only (refresh)]))

(def is-dev? (env :is-dev))