# Hackersome

[![Build Status](https://travis-ci.org/bcambel/hackersome.svg?branch=development)](https://travis-ci.org/bcambel/hackersome)

A Combination of multiple portals

- [Python Hackers](http://pythonhackers.com)
- [Clojure Hackers](http://clojurehackers.com)
- [Hackersome](http://hackersome.com)
- [Sweet.io](http://sweet.io)

## Development

Start a REPL (in a terminal: `lein repl`, or from Emacs: open a
clj/cljs file in the project, then do `M-x cider-jack-in`. Make sure
CIDER is up to date).

In the REPL do

```clojure
(def sys (startup {}))
```

Which will return the system map. It's based on Stuart Sierra's amazing Component lib.

In order to access db session component do a 

```clojure
(:connection (:db sys))
``` 

The call to `(startup {})` does two things, it starts the webserver at port
10554, . Give them some time to start.


## Trying it out

If all is well you now have a browser window saying 'Hello Chestnut',
and a REPL prompt that looks like `cljs.user=>`.

Open `resources/public/css/style.css` and change some styling of the
H1 element. Notice how it's updated instantly in the browser.

Open `src/cljs/hsui/core.cljs`, and change `dom/h1` to
`dom/h2`. As soon as you save the file, your browser is updated.

In the REPL, type

```
(ns hsm.core)
(swap! app-state assoc :text "Interactivity FTW")
```

Notice again how the browser updates.


Docker
---------------

A sample clojure docker container could be used.

```bash
docker pull clojure
docker run -i -t --entrypoint /bin/bash <imageID>
```

Cassandra 
==========

Using the following [Spotify Cassandra Container](https://github.com/spotify/docker-cassandra)


Runs a command inside the given container ID. 
since image runs with [cassandra -f option](https://github.com/spotify/docker-cassandra/blob/master/cassandra/scripts/cassandra-singlenode.sh#L33), you wont get any interaction

```
docker pull spotify/cassandra
# 
docker run spotify/cassandra -d --ip=172.17.0.48
# fetch the IP of the image
docker inspect <image_id> 
# modify your /etc/hosts file to add the cassandra host
docker exec -it <container_id> cqlsh < schema.cql
```

## License

Copyright © 2014 Bahadir Cambel

Distributed under the MIT License.
