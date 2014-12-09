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
(startup {})
(browser-repl)
```

The call to `(startup {})` does two things, it starts the webserver at port
10554, and also the Figwheel server which takes care of live reloading
ClojureScript code and CSS. Give them some time to start.

Running `(browser-repl)` starts the Weasel REPL server, and drops you
into a ClojureScript REPL. Evaluating expressions here will only work
once you've loaded the page, so the browser can connect to Weasel.

When you see the line `Successfully compiled "resources/public/app.js"
in 21.36 seconds.`, you're ready to go. Browse to
`http://localhost:10554` and enjoy.


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



## License

Copyright © 2014 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
