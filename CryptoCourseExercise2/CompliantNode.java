
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/* CompliantNode refers to a node that follows the rules (not malicious)*/
public class CompliantNode implements Node {

	private boolean[] _followees;
	
	private HashSet<Integer> _receivedLastRoundFrom;
//	private boolean[] _blacklist;
//	private Set<Transaction> _initalTxs;
	
	private Set<Transaction> _txpool;
	
	private double _p_graph, _p_malicious, _p_txDistribution;
	private int _numRounds;
	private int _currentRound;
	
	
    public CompliantNode(double p_graph, double p_malicious, double p_txDistribution, int numRounds) {
		this._p_graph = p_graph;
		this._p_malicious = p_malicious;
		this._p_txDistribution = p_txDistribution;
		this._numRounds = numRounds;
		_txpool = new HashSet<Transaction>();
		_currentRound = 0;
		_receivedLastRoundFrom = new HashSet<Integer>();
    }

    public void setFollowees(boolean[] followees) {
    	_followees = followees;
    }

    public void setPendingTransaction(Set<Transaction> pendingTransactions) {
    	for (Transaction tx : pendingTransactions) {
    		_txpool.add(tx);
    	}
    }

    public Set<Transaction> sendToFollowers() {
        Set<Transaction> txsToForward = new HashSet<Transaction>();
        for(Transaction tx : _txpool) {
        	txsToForward.add(tx);
        }
        _txpool.clear();
        return txsToForward;
    }

    public void receiveFromFollowees(Set<Candidate> candidates) {  
    	
    	//HashSet<Integer> receivedInThisRound = new HashSet<Integer>();
    	if (_currentRound == 0)  {   	
	    	for (Candidate c : candidates) {	    		   		
	    		if(_followees[c.sender]) {
	    			_txpool.add(c.tx);
	    			_receivedLastRoundFrom.add(c.sender); 
	    		}
	    	}    	
    	} else if (_currentRound > 0) {
	    	for (Candidate c : candidates) {
	    		if(_followees[c.sender]) {
		    		if(!_receivedLastRoundFrom.contains(c.sender)) {
		    			_followees[c.sender] = false;
		    		} else {
			    		_txpool.add(c.tx);
			    		_receivedLastRoundFrom.add(c.sender); 
			    	}		    			
		    	}
	    	}
	    	
	    }	
    	
		// check if sender is in followees

    	_currentRound += 1;
    }
}
