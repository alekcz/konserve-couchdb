# konserve-couchdb

A couchdb backend for [konserve](https://github.com/replikativ/konserve) implemented with [carmine](https://github.com/ptaoussanis/carmine). 


# Status

![build](https://github.com/alekcz/konserve-redis/workflows/build/badge.svg?branch=master) [![codecov](https://codecov.io/gh/alekcz/konserve-redis/branch/master/graph/badge.svg)](https://codecov.io/gh/alekcz/konserve-redis) 

## Usage

[![Clojars Project](https://img.shields.io/clojars/v/alekcz/konserve-redis.svg)](http://clojars.org/alekcz/konserve-redis)

`[alekcz/konserve-redis "0.1.0-SNAPSHOT"]`

The purpose of konserve is to have a unified associative key-value interface for
edn datastructures and binary blobs. Use the standard interface functions of konserve.

You can provide the carmine redis connection specification map to the
`new-redis-store` constructor as an argument. We do not require additional
settings beyond the konserve serialization protocol for the store, so you can
still access the store through carmine directly wherever you need.

```clojure
(require '[konserve-redis.core :refer :all]
         '[clojure.core.async :refer [<!!] :as async]
         '[konserve.core :as k])
  
  (def redis-store (<!! (new-redis-store {:pool {} :spec {:uri "redis://localhost:6379/"}})))

  (<!! (k/exists? redis-store  "cecilia"))
  (<!! (k/get-in redis-store ["cecilia"]))
  (<!! (k/assoc-in redis-store ["cecilia"] 28))
  (<!! (k/update-in redis-store ["cecilia"] inc))
  (<!! (k/get-in redis-store ["cecilia"]))

  (defrecord Test [a])
  (<!! (k/assoc-in redis-store ["agatha"] (Test. 35)))
  (<!! (k/get-in redis-store ["agatha"]))
```




## License

Copyright © 2020 Alexander Oloo

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.