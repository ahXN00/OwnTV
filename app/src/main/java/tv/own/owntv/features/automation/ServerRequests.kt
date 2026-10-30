package tv.own.owntv.features.automation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The requests one run of the server answers, all under one job that [AutomationServer.stop] cancels:
 * a request still pressing keys or waiting on a route must not go on once the user switches
 * automation off. The job is a [SupervisorJob], so one request ending badly leaves the others alone,
 * and a child of [parent]'s job, so the app scope's own cancellation still reaches every request.
 */
internal class ServerRequests(parent: CoroutineScope) {
    private val job = SupervisorJob(parent.coroutineContext[Job])
    private val scope = CoroutineScope(parent.coroutineContext + job + Dispatchers.IO)

    /**
     * Runs [serve] as one of these requests. [done] runs once it is over, however it ends: also when it
     * is cancelled before it starts, so whatever [done] gives back is never lost. [done] must not throw.
     */
    fun launch(serve: suspend () -> Unit, done: () -> Unit) {
        scope.launch { serve() }.invokeOnCompletion { done() }
    }

    /** Cancels every request still running; any launched later is cancelled before it starts. */
    fun cancel() = job.cancel()
}
