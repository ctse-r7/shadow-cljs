(ns shadow.cljs.ui.db.builds
  (:require
    [shadow.experiments.grove.db :as db]
    [shadow.experiments.grove.events :as ev]
    [shadow.experiments.grove.eql-query :as eql]
    [shadow.cljs.model :as m]
    [shadow.cljs.ui.db.env :as env]
    [shadow.cljs.ui.db.relay-ws :as relay-ws]))

(defn forward-to-ws!
  {::ev/handle
   [::m/build-watch-compile!
    ::m/build-watch-stop!
    ::m/build-watch-start!
    ::m/build-compile!
    ::m/build-release!
    ::m/build-release-debug!]}
  [env {:keys [e build-id]}]
  {:relay-send
   [{:op e
     :to 1 ;; FIXME: don't hardcode CLJ runtime id
     ::m/build-id build-id}]})

(defmethod eql/attr ::m/active-builds
  [env db _ query-part params]
  (->> (db/all-of db ::m/build)
       (filter ::m/build-worker-active)
       (sort-by ::m/build-id)
       (map :db/ident)
       (into [])))

(defmethod eql/attr ::m/build-sources-sorted
  [env db current query-part params]
  (when-let [info (get-in current [::m/build-status :info])]
    (let [{:keys [sources]} info]
      (->> sources
           (sort-by :resource-name)
           (vec)
           ))))

(defmethod eql/attr ::m/build-warnings-count
  [env db current query-part params]
  (let [{:keys [warnings] :as info} (::m/build-status current)]
    (count warnings)))

(defmethod eql/attr ::m/build-runtimes
  [env db current query-part params]
  (let [build-ident (get current :db/ident)
        {::m/keys [build-id] :as build} (get db build-ident)]

    (->> (db/all-of db ::m/runtime)
         (filter (fn [{:keys [runtime-info]}]
                   (and (= :cljs (:lang runtime-info))
                        (= build-id (:build-id runtime-info)))))
         (mapv :db/ident))))

(defmethod eql/attr ::m/build-runtime-count
  [env db current query-part params]
  (let [build-ident (get current :db/ident)
        {::m/keys [build-id] :as build} (get db build-ident)]

    (->> (db/all-of db ::m/runtime)
         (filter (fn [{:keys [runtime-info]}]
                   (and (= :cljs (:lang runtime-info))
                        (= build-id (:build-id runtime-info)))))
         (count))))

(defmethod relay-ws/handle-msg ::m/sub-msg
  [{:keys [db] :as env} {::m/keys [topic] :as msg}]
  (case topic
    ::m/build-status-update
    (let [{:keys [build-id build-status]} msg
          build-ident (db/make-ident ::m/build build-id)]
      {:db (assoc-in db [build-ident ::m/build-status] build-status)})

    ::m/supervisor
    (let [{::m/keys [worker-op build-id]} msg
          build-ident (db/make-ident ::m/build build-id)]
      (case worker-op
        :worker-stop
        {:db (assoc-in db [build-ident ::m/build-worker-active] false)}
        :worker-start
        {:db (assoc-in db [build-ident ::m/build-worker-active] true)}

        (js/console.warn "unhandled supervisor msg" msg)))

    (do (js/console.warn "unhandled sub msg" msg)
        {})))