(ns hsm.helpers)

(defn pl->lang
	[platform]
	(condp = platform
		"cpp" "C++"
		"CSharp" "C#"
		platform))