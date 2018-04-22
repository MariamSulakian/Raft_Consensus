package edu.duke.raft;

import java.util.*;

public class LeaderMode extends RaftMode {
  private Timer heartbeatTimer;
  private int heartbeatTimer_ID = 3;
  private boolean switchedMode = true;

  public void go () {
    synchronized (mLock) {
      // System.out.println(mConfig.getTimeoutOverride());
      //int term = 0;
      System.out.println ("S" + 
			  mID + 
			  "." + 
			  mConfig.getCurrentTerm() + 
			  ": switched to leader mode.");
      //log_repair();
      Entry[] logEntries = new Entry[mLog.getLastIndex() +1];
      for (int i=0; i<mLog.getLastIndex()+1; i++) {
        logEntries[i] = mLog.getEntry(i);
      }
      for(int i =1; i<=mConfig.getNumServers();i++){
        remoteAppendEntries(i, mConfig.getCurrentTerm(), mID, mLog.getLastIndex(), mLog.getLastTerm(), logEntries, mCommitIndex);
      }

      switchedMode = false;

      // initiate timer
      heartbeatTimer = scheduleTimer(HEARTBEAT_INTERVAL, this.heartbeatTimer_ID);
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
      int vote = term;

      // check if candidate has larger term than curr leader
      if (candidateTerm > term) {
        this.heartbeatTimer.cancel();
        if(!switchedMode){
          switchedMode = true;
          RaftServerImpl.setMode(new FollowerMode());
        }
      return 0;
      }
      return vote;
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
      // current leader
      int term = mConfig.getCurrentTerm ();
      int result = term;

      //if result is higher term (trying to append higher term) then curr leader switches to follower mode
      if (leaderTerm > term){
        this.heartbeatTimer.cancel();
        mConfig.setCurrentTerm(leaderTerm, 0);
        if (!switchedMode){
          switchedMode = true;
          RaftServerImpl.setMode(new FollowerMode());
        }
        return 0;
      }
      return result;
    }
  }

  // @param id of the timer that timed out
  public void handleTimeout (int timerID) {
    synchronized (mLock) {
      if(timerID == this.heartbeatTimer_ID){
        Entry[] logEntries = new Entry[mLog.getLastIndex() +1];
        for (int i=0; i<mLog.getLastIndex()+1; i++) {
          logEntries[i] = mLog.getEntry(i);
        }
        for(int i =1; i<=mConfig.getNumServers();i++){
          remoteAppendEntries(i, mConfig.getCurrentTerm(), mID, mLog.getLastIndex(), mLog.getLastTerm(), logEntries, mCommitIndex);
        }
        this.heartbeatTimer.cancel();
        heartbeatTimer = scheduleTimer(HEARTBEAT_INTERVAL, this.heartbeatTimer_ID);
        // log_repair();
      }

    }
  }
}

  // leader must find earliest log entry that it has in common with each follower
  // then roll followers forward until they have logs identical to leader's
//   private void log_repair(){
//     int term = mConfig.getCurrentTerm();
//     int lastInd = mLog.getLastIndex();
//     int numServers = mConfig.getNumServers();
//     int[] matchingLogs = new int[6];
//     int reply = -1; // set to -1 until we have a match
//     int start = 1;

//     RaftResponses.setTerm(term);
//     Arrays.fill(matchingLogs, lastInd);

//     while (start <= numServers){      
//       // while no matching indexes
//       while (reply != 0){
//         ArrayList<Entry> entries = new ArrayList<Entry>();

//         int here = matchingLogs[start];
//         int next = lastInd + 1;
//         while (here < next){
//           Entry wantedEntry = mLog.getEntry(here);
//           entries.add(wantedEntry);
//           here++;
//         }

//         int entryListSize = entries.size();
//         Entry[] logEntries = new Entry[entryListSize];
//         logEntries = entries.toArray(logEntries);

//         // accessing responses to RPC via RaftResponse class
//         // "remote" methods have no return value
//         remoteAppendEntries(start, term, mID, matchingLogs[start], mLog.getEntry(matchingLogs[start]).term, logEntries, mCommitIndex);
//         Entry[] logEntries = new Entry[mLog.getLastIndex() +1];
//         for (int i=0; i<mLog.getLastIndex()+1; i++) {
//           logEntries[i] = mLog.getEntry(i);
//         }
//         for(int i =0; i<=mConfig.getNumServers();i++){
//           remoteAppendEntries(i, mConfig.getCurrentTerm(), mID, mLog.getLastIndex(), mLog.getLastTerm(), logEntries, mCommitIndex);
//         }
        
//         matchingLogs[start]--;

//         int [] replies = RaftResponses.getAppendResponses(term);
//         reply = replies[start];
//       }
//       start++;
//     }
//   }
// }
