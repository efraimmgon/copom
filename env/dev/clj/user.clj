(ns user
  "Userspace functions you can run by default in your local REPL."
  (:require
    [copom.config :refer [env]]
    [clojure.java.jdbc :as jdbc]
    [clojure.repl :refer :all]
    [clojure.spec.alpha :as s]
    [clojure.tools.namespace.repl :refer [refresh]]
    [expound.alpha :as expound]
    [mount.core :as mount]
    [copom.figwheel :refer [start-fw stop-fw cljs]]
    [copom.core :refer [start-app]]
    [copom.db.core :refer [*db*]]
    [conman.core :as conman]
    [luminus-migrations.core :as migrations]))

(alter-var-root #'s/*explain-out* (constantly expound/printer))

(add-tap (bound-fn* clojure.pprint/pprint))

(defn start 
  "Starts application.
  You'll usually want to run this on startup."
  []
  (mount/start-without #'copom.core/repl-server)
  (jdbc/execute! *db* ["PRAGMA foreign_keys = ON"]))

(defn stop 
  "Stops application."
  []
  (mount/stop-except #'copom.core/repl-server))

(defn restart 
  "Restarts application."
  []
  (stop)
  (start))

(defn restart-db 
  "Restarts database."
  []
  (mount/stop #'copom.db.core/*db*)
  (mount/start #'copom.db.core/*db*)
  (binding [*ns* 'copom.db.core]
    (conman/bind-connection copom.db.core/*db* "sql/queries.sql")))

(defn reset-db 
  "Resets database."
  []
  (migrations/migrate ["reset"] (select-keys env [:database-url])))

(defn migrate 
  "Migrates database up for all outstanding migrations."
  []
  (migrations/migrate ["migrate"] (select-keys env [:database-url])))

(defn rollback 
  "Rollback latest database migration."
  []
  (migrations/migrate ["rollback"] (select-keys env [:database-url])))

(defn create-migration 
  "Create a new up and down migration file with a generated timestamp and `name`."
  [name]
  (migrations/create name (select-keys env [:database-url])))

(defn reset [] (refresh))

(comment
  (start)
  (create-migration "add-entity-superscription-table")
  (reset-db)
  (migrate))
