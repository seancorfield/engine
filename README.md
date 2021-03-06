# engine [![Join the chat at https://gitter.im/seancorfield/engine](https://badges.gitter.im/seancorfield/engine.svg)](https://gitter.im/seancorfield/engine?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

This was an interesting thought experiment based on the idea that I could completely separate data sources, business logic, and data sinks. It introduced two abstractions: Queryable and Committable. A Queryable could be queried in some arbitrary way: it could be a JDBC data source, a hash map, etc. A `query` function allowed the business logic to run a query against any named input. A Committable could be given a description of an update (an insert or an actual update) that it knew how to execute. An `update` function allowed the business logic to signal that it needed an update committed after successful execution. When I ran the concept past a few people on Slack, they mostly opined that such abstractions were too abstract and that you couldn't create a realistic, usable abstraction that would work effectively over a wide range of data sinks.

Having used Engine at World Singles for a particular use case (validating and updating various member profile attributes as part of an API), I'm inclined to agree with those people: Engine proved verbose to use and isolating all mutations into things that looked like data sinks was quite painful (including creating one-off URL tokens that were needed by other mutations, such as sending emails). In addition, the core concept of running an "Engine request" through your entire business logic produced monadic code that was very hard to read and non-idiomatic, from a Clojure point of view. We've recently rewritten that code to use "native" Clojure to query data sources and access hash maps etc, and to create a simple pipeline of closures to be executed on success. The result is much simpler, more idiomatic code (which could still stand a bit more simplification but is already an improvement on Engine-based code).

That's why I'm sunsetting Engine before it gets any traction. "So long, and thanks for all the fish!", as they say.

# The Original README

A Clojure library designed to help separate business logic from
persistence by maintaining a strict query -> logic -> updates
workflow across your application.

The basic premise is that for each "request" or "unit of work", you create an `engine`
instance with a set of named data sources/sinks. You think perform all of your _pure_
business logic on/with the _engine_ and you tell it what updates you want made to
the data stores as you go along, but those are all queued up and only happen when the
_engine's_ work is `commit!`ed at the end of the process.

Your business logic can run queries against the data sources in the _engine_ and those
are assumed to be pure as well since they are intended to be readonly (see caveat below).
The _engine_ operates in two modes: _normal_ mode, which is assumed to generate a result
and a series of updates to perform, and _failure_ mode, which is assumed to generate an
exception and a different series of updates to perform. Failure mode can be `recover`ed
in a similar way to how `try`/`catch` works with exception.

Caveat about readonly data sources: while updates are deferred by the _engine_,
queries are run as requested and will return whatever the current data store's state
reflects. That means that queries are not idempotent: the same `query` function may
return different values on consecutive calls, if something else has modified the state
of the data store. For example, a JDBC data store will return the current state of
the database which can change over time. A trivial data store that would also exhibit
this behavior would be a clock/timer or a random number generator.

Since data stores are given to the _engine_ when it is constructed, for testing you
could easily pass in a mocked version for easier testing if you needed to do so.

## Usage

Engine provides a core workflow (as a protocol and a set of helper functions), and a
couple of data sources to get you up and running. The basic usage model is to create
all your data sources up front, and then for each "request" or "unit of work" you
create an _engine_ that you pass around your application, and finally you run `commit!`
on it to apply any updates and get your result out. Or, if the _engine_ is in failure
mode, you'll get an exception (after it has applied any failure-specific updates).

    (require '[engine.core :as e])
    (require '[engine.data.jdbc :as j])
    (require '[engine.data.memory :as m])
    ;; create a couple of data sources for the app
    (def db (j/jdbc-data-source
      {:dbtype "mysql" :dbname "mydb" :user "me" :password "secret"}))
    (def ram (m/in-memory-data-source))

Other data source types will be added in the future but it's fairly easy to create your
own, based on the `engine.queryable/Queryable` and `engine.committable/Committable`
protocols.

    ;; create workflow from data sources, with default
    (def app (e/engine {:db db :ram ram} :db))

This sets up an _engine_ with two named data sources and specifies that `:db` is the default
(so it can be omitted in most _engine_ operations, for convenience). Then you would pass `app`
through all of your code, as if threaded like this:

    (-> app
        ;; indicate desired updates
        (e/update :user {:id 9 :username "nine"})
        (e/update :user {:id 10 :username "ten"})
        (e/delete :user 11)
        (e/update :ram :name "Sean Corfield")
        ;; indicate intended result
        (e/return 42)
        ;; commit changes
        (e/commit!))
    ;; returns the result and applies the updates

In addition, you can ask the _engine_ to run queries against your data stores:

    (e/query app ["select id,username from user where id < ?" 12])
    ;; returns a result set from the db
    (e/query app :ram :name)
    ;; returns the value associated with :name in ram

The syntax for the query is specific to each type of data store. If the first argument (after
the _engine_ value) is
a keyword and there is more than one argument, it is treated as the name of a data store to
use for that query. So `(e/qquery app :name)` is treated as the query `:name` on the default
data store (which would be an error in our example since that would be a JDBC data store
which expects a vector containing SQL and parameters), whereas `(e/query app :ram :name)`
is treated as the query `:name` on the `:ram` data store.

Here's an example flow with a conditional failure in the middle:

    (-> app
        ;; write a log record if something fails
        (e/update-on-failure :logtable {:message "We failed!"})
        ...
        ;; later on we may fail
        (e/ifq #(< (e/query % :value) threshold)
          safe-process ;; called on app
          #(e/fail % (ex-info "Too big!" {:value value})))
        ;; we can continue here because the operations know
        ;; about normal/failure modes
        ;; this update will only happen in normal mode
        (e/update :sale {:name "Product" :amount value})
        ;; either write to logtable and throw the exception
        ;; or write to sale and return the engine's value
        (e/commit!))

`ifq` applies a query function to the _engine_ and then calls the appropriate function
on the _engine_ (for truthy, for falsey, or for failure). `ifp` applies a predicate
to the current _state_ of the _engine_. There are threaded versions of both to make
life easier in pipelines. In addition there are `condq->` and `condp->` to support
natural cascades of `ifq` and `ifp` operations (but only with pairs of query/predicate
and truthy functions).

If an _engine_ is in failure mode, you can still run queries but you cannot set a `return`
value, nor `transform` the current value, nor add any `update`s or `delete`s -- they are
all treated as no-ops. You can add `update-on-failure`s and `delete-on-failure`s at any
time -- in either normal mode or failure mode -- but they will only be applied if the
_engine_ is still in failure mode when you `commit!`.

If an _engine_ is in failure mode and you `commit!` the results, the failure value will
be thrown (so it must be an exception, technically a `java.lang.Throwable`). If you
are working with non-exception failure values, you can instead call `do-commit!` which
behaves like `commit!` in terms of updates and deletes to be applied but will yield
`nil` for success and the failure value itself for a failure. This is useful when
you are running the _engine_ for side-effects on success and error codes on failure.

You can choose whether entering failure mode should clear any pending updates. It is expected
that the default will be `fail`, as above, but you can also `commit-and-fail` which leaves any
pending updates in place before entering failure mode. Note that no new updates will be added
while in failure mode, and unless the _engine_ is `recover`ed, those earlier pending updates
will not be applied on a `commit!`.

You can `recover` from failure mode for specific exceptions:

    (e/recover app IllegalArgumentException f)

or using a general predicate:

    (e/recover app some-predicate f)

This resets the _engine_ to normal mode, removes any pending "on failure" updates and
deletes, and then calls `f` on the _engine_. `f` is assumed to be a workflow-aware function
that returns an updated _engine_. It is passed the previous failure value, in addition to
the _engine_.

## License

Copyright © 2015-2016 Sean Corfield

Distributed under the Eclipse Public License version 1.0.
