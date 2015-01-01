(ns hsm.helpers)

(defn pl->lang
	[platform]
	(condp = (clojure.string/lower-case platform)
		"cpp" "C++"
		"csharp" "C#"
    "python" "Python"
    "clojure" "Clojure"
    "clj" "Clojure"
		platform))