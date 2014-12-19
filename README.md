# Hackersome

[![Build Status](https://travis-ci.org/bcambel/hackersome.svg?branch=development)](https://travis-ci.org/bcambel/hackersome)

A Combination of multiple portals

- [Python Hackers](http://pythonhackers.com)
- [Clojure Hackers](http://clojurehackers.com)
- [Hackersome](http://hackersome.com)
- [Sweet.io](http://sweet.io)

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
(def sys (startup {}))
```

Which will return the system map. It's based on Stuart Sierra's amazing [Component lib](https://github.com/stuartsierra/component).

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

Kafka
==========

Using [Ches/Kafka](https://registry.hub.docker.com/u/ches/kafka/) as the base image. It's very simple to 
setup and use. Kafka requires ZooKeeper to keep track of it's client markers, nodes, etc..

```shell
docker run -d --name zookeeper jplock/zookeeper:3.4.6
docker run -d --name kafka --link zookeeper:zookeeper ches/kafka
ZK_IP=$(docker inspect --format '{{ .NetworkSettings.IPAddress }}' zookeeper)
KAFKA_IP=$(docker inspect --format '{{ .NetworkSettings.IPAddress }}' kafka)

docker exec -it daf936345971 kafka-topics.sh --create --topic test --replication-factor 1 --partitions 1 --zookeeper $ZK_IP:2181
docker exec -it daf936345971 kafka-console-producer.sh --topic test --broker-list $KAFKA_IP:9092
```

## License

Copyright © 2014 Bahadir Cambel

Distributed under the MIT License.
