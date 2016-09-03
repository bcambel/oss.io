# Hackersome

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/bcambel/hackersome?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

[![Build Status](https://travis-ci.org/bcambel/hackersome.svg?branch=development)](https://travis-ci.org/bcambel/hackersome)

[![Coverage Status](https://coveralls.io/repos/bcambel/hackersome/badge.png)](https://coveralls.io/r/bcambel/hackersome)


[![](http://strck.hackersome.com/pixel.gif)]()

A Community project for developers, entreprenuers, hustlers. Hackersome is the umbrella project name, and includes the following portals

- [Python Hackers](http://pythonhackers.com)
- [Clojure Hackers](http://clojurehackers.com)
- [Hackersome](http://hackersome.com)


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


Docker
---------------

Install docker via the [lxc-docker package](https://gist.github.com/bcambel/ba55a02124831388c4bc) in Ubuntu.
If you're in MacOS use the [Boot2Docker image](http://boot2docker.io/). Don't forget to install ```fig``` as well. If you have python & pip already you may install via ```pip install fig```

Modify the ```/etc/default/docker``` and add/enable the following lines

```
DOCKER_OPTS="--dns 8.8.8.8 --dns 8.8.4.4"
DOCKER_OPTS="-H tcp://127.0.0.1:4243 -H unix:///var/run/docker.sock"
```

and check if the docker service is running via

```
service docker status
```

A sample clojure docker container could be used to play around, get used to.

```bash
docker pull clojure
docker run -i -t --entrypoint /bin/bash <imageID>
```


## License

Copyright © 2016 Bahadir Cambel

Distributed under the MIT License.

