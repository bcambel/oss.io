# OSS.io - Open Source Software Community

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/bcambel/hackersome?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

[![Build Status](https://travis-ci.org/bcambel/hackersome.svg?branch=development)](https://travis-ci.org/bcambel/hackersome)

[![Coverage Status](https://coveralls.io/repos/bcambel/hackersome/badge.png)](https://coveralls.io/r/bcambel/hackersome)


[![](http://strck.hackersome.com/pixel.gif)]()

A Community project for developers, entreprenuers, hustlers.


## Development

Create a profiles.clj file in the root folder of the project with the following settings

```clojure
{:dev  {:env {
			;;twitter settings
			:app-consumer-key ""
			:app-consumer-secret ""
			:user-access-token ""
			:user-access-token-secret ""
			;;github settings
			:client-id ""
			:client-secret ""
}}}
```

Start a REPL (in a terminal: `lein repl`, or from Emacs: open a
clj/cljs file in the project, then do `M-x cider-jack-in`. Make sure
CIDER is up to date).

In the REPL do

```clojure
(def sys (startup {:conf "settings.dev.ini"}))
```

Which will return the system map. It's based on Stuart Sierra's amazing [Component lib](https://github.com/stuartsierra/component).

In order to access db session component do a

```clojure
(:connection (:db sys))
```

The call to `(startup {})` does two things, it starts the webserver at port
10554, . Give them some time to start.

# List
- https://github.com/clojars/clojars-web/wiki/data


## License

Copyright © 2017 Bahadir Cambel

Distributed under the MIT License.
