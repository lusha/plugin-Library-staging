/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.serl;

/**
** A progress that has one single part.
**
** @author infinity0
*/
public class AtomicProgress implements Progress {

	protected boolean done;
	protected TaskAbortException abort = null;

	public AtomicProgress() { }

	public synchronized void setDone() {
		done = true;
		notifyAll();
	}

	public synchronized void setAbort(TaskAbortException e) {
		abort = e;
		notifyAll();
	}

	/*========================================================================
	  public interface Progress
	 ========================================================================*/

	@Override public int partsDone() {
		return (done)? 1: 0;
	}

	@Override public int partsTotal() {
		return 1;
	}

	@Override public boolean isTotalFinal() {
		return true;
	}

	@Override public synchronized void join() throws InterruptedException, TaskAbortException {
		while (!done && abort == null) { wait(); }
		if (abort != null) {
			throw abort;
		}
	}

}
