(ns hackersome-web.core-test
  (:require [clojure.test :refer :all]
            [hackersome.web.core :refer :all])
  (:use ring.mock.request))


(deftest your-handler-test
  (is (= (startup (request :get "/welcome"))
         {:status 200
          :headers {"content-type" "text/plain"}
          :body "Your expected result"})))