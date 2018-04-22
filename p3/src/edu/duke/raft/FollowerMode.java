package edu.duke.raft;
import java.util.*;

public class FollowerMode extends RaftMode {
  private Timer timeout;
  private int heartbeatTimer_ID = 0;
  private long nn;
  private boolean switchedMode = true;
  public void go () {
    synchronized (mLock) {
      // System.out.println(mConfig.getTimeoutOverride());
      //int term = 0;
      System.out.println ("S" + 
			  mID + 
			  "." + 
			  mConfig.getCurrentTerm() + 
			  ": switched to follower mode.");
      //timeout = scheduleTimer(HEARTBEAT_INTERVAL, heartbeatTimer_ID);   
      Random rn = new Random();
      int n = rn.nextInt(ELECTION_TIMEOUT_MAX-ELECTION_TIMEOUT_MIN+1) + ELECTION_TIMEOUT_MIN;
      nn = n;
      switchedMode = false;
      timeout = scheduleTimer(nn, heartbeatTimer_ID);  
      //System.out.println(timeout);
    }   
  }
  
  // @param candidate’s term
  // @param candidate requesting vote
  // @param index of candidate’s last log entry
  // @param term of candidate’s last log entry
  // @return 0, if server votes for candidate; otherwise, server's
  // current term
  public int requestVote (int candidateTerm,
			  int candidateID,
			  int lastLogIndex,
			  int lastLogTerm) {
    synchronized (mLock) {
      int term = mConfig.getCurrentTerm ();
      int vote = 0;
      //int votedFor = mConfig.getVotedFor(); 
      int myLastTerm = mLog.getLastTerm();
      int myLastIndex = mLog.getLastIndex();
      if (candidateTerm > term) {
        if((myLastTerm>lastLogTerm) || (lastLogTerm==myLastTerm && lastLogIndex<myLastIndex)){
          vote = term;
         // if ((votedFor==0 || votedFor ==candidateID) && (mLog.getLastIndex()==lastLogIndex && mLog.getLastTerm()>lastLogTerm)) {//if votedFor is null or candidateID and candidate's log is at least up-to-date as receiver's log
        }
        if (vote==term){
          mConfig.setCurrentTerm(candidateTerm,mID);
          term = mConfig.getCurrentTerm();
          vote = term;
          // System.out.println("S" + mID + "." + term + " = casted vote for self");
        }
        else {
          mConfig.setCurrentTerm(candidateTerm,candidateID);
          term = mConfig.getCurrentTerm();
          // System.out.println("S" + mID + "." + term + " = casted vote for " + candidateID + "." + candidateTerm);
        }
        return vote;
      }
      return term;
      // return vote;
    }
  }
  

  // @param leader’s term
  // @param current leader
  // @param index of log entry before entries to append
  // @param term of log entry before entries to append
  // @param entries to append (in order of 0 to append.length-1)
  // @param index of highest committed entry
  // @return 0, if server appended entries; otherwise, server's
  // current term
  public int appendEntries (int leaderTerm,
			    int leaderID,
			    int prevLogIndex,
			    int prevLogTerm,
			    Entry[] entries,
			    int leaderCommit) {
    synchronized (mLock) {
      int term = mConfig.getCurrentTerm ();
      if (leaderTerm > term) {
        timeout.cancel();
        timeout = scheduleTimer(nn, heartbeatTimer_ID);
      	mConfig.setCurrentTerm(leaderTerm, 0);
        term = mConfig.getCurrentTerm();
      }

      if (leaderTerm == mConfig.getCurrentTerm()) { //if true leader, copy their whole log    
        //timeout.cancel();
        mLog.insert(entries, -1, -1);  
        //timeout = scheduleTimer(HEARTBEAT_INTERVAL, heartbeatTimer_ID);
        return 0;     
      }
      return term;
    }
  }  

  // @param id of the timer that timed out
  public void handleTimeout (int timerID) { //assuming timer has already timed out
    synchronized (mLock) {
    	if (timerID == heartbeatTimer_ID) {
        timeout.cancel();
        if (!switchedMode){
          switchedMode = true;
          RaftServerImpl.setMode(new CandidateMode());
        }
    	}
    }
  }
}

