(ns sixsq.nuvla.auth.oidc-test
  (:require
    [clj-http.client :as http]
    [clojure.test :refer :all]
    [sixsq.nuvla.auth.oidc :as t]))

(deftest get-access-token
  (with-redefs [http/post (constantly {:body "{\"access_token\": \"a\"}"} )]
    (is (= (t/get-access-token nil nil nil nil nil) "a"))))

(deftest get-id-token
  (with-redefs [http/post (constantly {:body "{\"id_token\": \"a\"}"} )]
    (is (= (t/get-id-token nil nil nil nil nil) "a"))))

(deftest get-token
  (with-redefs [http/post (fn [] (throw (ex-info "" {})))]
    (is (nil? (t/get-token nil nil nil nil nil)))))

(deftest get-kid-from-id-token
  (is (= (t/get-kid-from-id-token nil) nil))
  (is (= (t/get-kid-from-id-token "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6IjJaUXBKM1VwYmpBWVhZR2FYRUpsOGxWMFRPSSJ9.eyJhdWQiOiIzODg3N2YzZi1lOTU4LTRjMmItYjU0My1iZmE3Y2RlN2JkZTgiLCJpc3MiOiJodHRwczovL2xvZ2luLm1pY3Jvc29mdG9ubGluZS5jb20vOWU4YTUzMzQtNDk3Yy00ZDhhLWE3OTctNzk5N2NmOGNjNzYzL3YyLjAiLCJpYXQiOjE2NjE1MDQwNzEsIm5iZiI6MTY2MTUwNDA3MSwiZXhwIjoxNjYxNTA3OTcxLCJlbWFpbCI6ImZkZW5pZ2VyQGljcmMub3JnIiwicmgiOiIwLkFUQUFORk9Lbm54SmlrMm5sM21YejR6SFl6OV9oemhZNlN0TXRVT19wODNudmVnd0FGSS4iLCJzdWIiOiJMRHdKUjNudXVkZkk5NVBhMXVfeGMzUDJZWDR3Qk9pMjh4ZzdCVl9NeFVrIiwidGlkIjoiOWU4YTUzMzQtNDk3Yy00ZDhhLWE3OTctNzk5N2NmOGNjNzYzIiwidXRpIjoiWElNSU9vN3lwME9Tb25OZkRDbzdBQSIsInZlciI6IjIuMCJ9.GfNULmPtBtxsUn70HHoUr6mRAEx1u_xCDWEl0T0AoWOUPAAgBMsPGhtYHJ5zmBs62Wb1BKeQDEo_hmzrINLxJxq9laU9dMDj6zm2gjXVNZeKEQ87vCtfgOtLYgDNS2glVQmg5okm0E2XwLcQwsmWZWh8lhwiLYuUQwMl0Eq_JquXHJapZWYbueUWOSajZ8rB8MBGoJH1SwDhHJyFzqsncOZUrREeaPQPKxd6VGnmJ9rMWzaxSzmqQASqNkG08YS8ozxVdBJFBbBzTTW5ElV6teqyXg4u4RxH1hd2fb92RWKigarj6t93X4vofc6tUsRdyPhOYKYlKWp7AREgZwomxw") "2ZQpJ3UpbjAYXYGaXEJl8lV0TOI")))


(deftest get-public-key
  (with-redefs [http/get (constantly {:body "{\"keys\": [{\"kty\": \"RSA\", \"use\": \"sig\", \"kid\": \"YXRIVkpk\", \"n\": \"2lJ04IzyohRpv9cziO8XlOp17osnmTeFFyzrrNEO6xPtKZcAnroP42gG5Tid61yb5rkM0NMQrnMxZCH3UevLQJFy-T7tzKPctaxAERo8SsWw1rOt2g9GnhUKB9aQkaYzmTLm4_WnshPQjw-KG2X5A-HWshfYunekpMsMsscuRCC4v7H59C-eoAY3SadzFe6WHnuaqtECsIrQfc5_o7uvkD2-q29WqUyVc1CCjaT5h8jipGFVJl7BajlPkwbETkdIxNRLPx6kI5dN4zlw6TzJTI1tZfaMtSSJAulDR9HtWLawoqw5k51K9K3SEPMaXJjjw7Qzsxk_e3ePjCmaQ6Iezw\", \"e\": \"AQAB\"}, {\"kty\": \"EC\", \"use\": \"sig\", \"kid\": \"TE5pM2lq\", \"crv\": \"P-256\", \"x\": \"FDxML-lNMpkPAjXDSX-fIClbNkfSe3wChL9OqAVOIGs\", \"y\": \"znt4S_unJJwOg9iaOMIdiepKvd-2s_bj5bG4L-wWCo8\"}]}"} )]
    (is (= (t/get-public-key "https://jwks-url" "TE5pM2lq")
           "{\"kty\":\"EC\",\"use\":\"sig\",\"kid\":\"TE5pM2lq\",\"crv\":\"P-256\",\"x\":\"FDxML-lNMpkPAjXDSX-fIClbNkfSe3wChL9OqAVOIGs\",\"y\":\"znt4S_unJJwOg9iaOMIdiepKvd-2s_bj5bG4L-wWCo8\"}"))
    (is (= (t/get-public-key "https://jwks-url" "YXRIVkpk")
           "{\"kty\":\"RSA\",\"use\":\"sig\",\"kid\":\"YXRIVkpk\",\"n\":\"2lJ04IzyohRpv9cziO8XlOp17osnmTeFFyzrrNEO6xPtKZcAnroP42gG5Tid61yb5rkM0NMQrnMxZCH3UevLQJFy-T7tzKPctaxAERo8SsWw1rOt2g9GnhUKB9aQkaYzmTLm4_WnshPQjw-KG2X5A-HWshfYunekpMsMsscuRCC4v7H59C-eoAY3SadzFe6WHnuaqtECsIrQfc5_o7uvkD2-q29WqUyVc1CCjaT5h8jipGFVJl7BajlPkwbETkdIxNRLPx6kI5dN4zlw6TzJTI1tZfaMtSSJAulDR9HtWLawoqw5k51K9K3SEPMaXJjjw7Qzsxk_e3ePjCmaQ6Iezw\",\"e\":\"AQAB\"}")))
  (with-redefs [http/get (fn [] (throw (ex-info "" {})))]
    (is (nil? (t/get-public-key "https://jwks-url" "TE5pM2lq")))))
