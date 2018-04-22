package edu.duke.raft;
import java.util.*;

public class CandidateMode extends RaftMode {
  private Timer electionTimer;
  private Timer voteTimer;
  private int electionTimer_ID = 1;
  private int voteTimer_ID = 2;
  private long nn;
  private boolean switchedMode = true;

  public void go () {
    synchronized (mLock) {
      // System.out.println(mConfig.getTimeoutOverride());
      //int term = 0; 
      int newTerm = mConfig.getCurrentTerm()+1;
      mConfig.setCurrentTerm(newTerm, mID);
      System.out.println ("S" + 
			  mID + 
			  "." + 
			  newTerm + 
			  ": switched to candidate mode.");
      RaftResponses.setTerm(newTerm);
      RaftResponses.clearVotes(newTerm);
      //RaftResponses.setVote(mID,0,mConfig.getCurrentTerm(), RaftResponses.mRounds[mID]); //vote for yourself 
      for (int i=1; i<=mConfig.getNumServers(); i++) {
        if(i!=mID){
          remoteRequestVote(i, mConfig.getCurrentTerm(), mID, mLog.getLastIndex(), mLog.getLastTerm());
          //remoteRequestVote(i, mConfig.getCurrentTerm(), this.mID, mLastApplied, mLog.getLastTerm());
        }
      }
      Random rn = new Random();
      int n = rn.nextInt(ELECTION_TIMEOUT_MAX-ELECTION_TIMEOUT_MIN+1) + ELECTION_TIMEOUT_MIN;
      nn = n;
      switchedMode = false;
      electionTimer = scheduleTimer(nn, electionTimer_ID); 
      voteTimer = scheduleTimer(10, voteTimer_ID);
      //vote for yourself 
    }
  }
  //set timer on your election that is the duration of the election 
    //send out election stuff 
    

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
          electionTimer.cancel();
          voteTimer.cancel();
          if (!switchedMode){
            switchedMode = true;
            RaftServerImpl.setMode(new FollowerMode());
          }
        }
        return vote;
      }
      return term;
      //     timeout.cancel();
      //     mConfig.setCurrentTerm(candidateTerm,candidateID);
      //     timeout = scheduleTimer(nn, electionTimer_ID);     
      //     return 0;
      //   }
      // }
      // if(candidateID==mID){
      //   return 0;
      // }
      // else{
      //    return vote;
      // }
    }
  }
  //dont do log repair 
  //switch out of candidate mode and back to follower mode if get request from leader with a higher term
  

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
      int result = term;
      // System.out.println("S" + mID + "." + term + " (candidate): heartbeat received from S" + leaderID + "." + leaderTerm);
      if(leaderTerm > term){
        electionTimer.cancel();
        voteTimer.cancel();
        mConfig.setCurrentTerm(leaderTerm,0);
        //mConfig.setCurrentTerm(leaderTerm, 0);
        //mLog.insert(entries, -1, -1); 
        if (!switchedMode){
          switchedMode = true;
          RaftServerImpl.setMode(new FollowerMode());
        }
        return -1;
      }
      // System.out.println("S" + mID + "." + term + " (candidate): heartbeat ignored from S" + leaderID + "." + leaderTerm);
      return -1;
    }
  }

  //handletimeout: when timer expires have to check if have majority votes (become leader), if not cancel timer and start new timer (start new election) 
  //before start a new election, clear votes
  //if someone responds with a term that's greater then your term then go to follower mode 
  //cancel timer before switching modes
  // @param id of the timer that timed out
  public void handleTimeout (int timerID) {
    synchronized (mLock) {
      if(timerID==electionTimer_ID){
        int newTerm = mConfig.getCurrentTerm()+1;
        // System.out.println(mID + ": election timer runs out");
        electionTimer.cancel();
        voteTimer.cancel();
        mConfig.setCurrentTerm(newTerm, mID);
        RaftResponses.setTerm(newTerm);
        RaftResponses.clearVotes(newTerm);
        //RaftResponses.setVote(mID,0,mConfig.getCurrentTerm()); //vote for yourself 
        for (int i=1; i<=mConfig.getNumServers(); i++) {
          if(i!=mID){
            remoteRequestVote(i, mConfig.getCurrentTerm(), mID, mLog.getLastIndex(), mLog.getLastTerm());
            //remoteRequestVote(i, mConfig.getCurrentTerm(), this.mID, mLastApplied, mLog.getLastTerm());
          }
        }
        electionTimer = scheduleTimer(nn, electionTimer_ID); 
        voteTimer = scheduleTimer(10, voteTimer_ID);
      }
      else if(timerID == voteTimer_ID){
        int sameTerm = mConfig.getCurrentTerm();
        // System.out.println(mID + ": vote timer runs out");
        //this.timeout.cancel();
        int[] VotingResults = RaftResponses.getVotes(mConfig.getCurrentTerm());
        int votedforme = 0;
        // System.out.println("results: "+Arrays.toString(VotingResults));
        if(VotingResults!=null){
          for(int i=0;i<VotingResults.length;i++){
            // if (VotingResults[i] > sameTerm){
            //     electionTimer.cancel();
            //     voteTimer.cancel();
            //     mConfig.setCurrentTerm(VotingResults[i], 0);
            //     if(!switchedMode){
            //       switchedMode = true;
            //       RaftServerImpl.setMode(new FollowerMode());
            //     }
            //     return;
            // }
            if(VotingResults[i] == 0){
              votedforme++;
            }
          }
          if(votedforme > (mConfig.getNumServers()/2)){
            electionTimer.cancel();
            voteTimer.cancel();
            RaftResponses.clearVotes(mConfig.getCurrentTerm());
            if(!switchedMode){
              switchedMode = true;
              RaftServerImpl.setMode(new LeaderMode());
            }
          }  
            // else {
          //   RaftResponses.clearVotes(mConfig.getCurrentTerm());
          //   for (int i=1; i<=mConfig.getNumServers(); i++) {
          //     if(i!=mID){
          //       remoteRequestVote(i, mConfig.getCurrentTerm(), mID, mLog.getLastIndex(), mLog.getLastTerm());
          //   //remoteRequestVote(i, mConfig.getCurrentTerm(), this.mID, mLastApplied, mLog.getLastTerm());
          //     }
          //   }
          //   voteTimer.cancel();
          //   voteTimer = scheduleTimer(10, voteTimer_ID);
          // }
        }
        voteTimer.cancel();
        voteTimer = scheduleTimer(10, voteTimer_ID); 
      }
    }
  }
}
