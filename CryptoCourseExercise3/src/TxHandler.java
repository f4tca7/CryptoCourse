
import java.security.PublicKey;
import java.util.ArrayList;

public class TxHandler {

	protected UTXOPool _utxoPool;
	
    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
    	_utxoPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
    	
    	double totalIn = 0.0d;
    	double totalOut = 0.0d;
    	
    	ArrayList<UTXO> checkedInputs = new ArrayList<UTXO>();
    	for(int i = 0; i < tx.getInputs().size(); i++) {   
    		Transaction.Input input = tx.getInput(i);
    		if(input == null) {
    			return false;
    		}
    		
    		UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
    		// condition 1
    		if (!_utxoPool.contains(utxo) || checkedInputs.contains(utxo)) {
    			return false;
    		}
    		
    		Transaction.Output previousTxOutput = _utxoPool.getTxOutput(utxo);
    		PublicKey pk = previousTxOutput.address;   
    		// condition 2
    		if( !Crypto.verifySignature(pk, tx.getRawDataToSign(i), input.signature)) {
    			return false;
    		}
    		// condition 3
    		checkedInputs.add(utxo);
    		totalIn += previousTxOutput.value;
    	}
    	
    	for(int i = 0; i < tx.getOutputs().size(); i++) {
    		double outVal = tx.getOutput(i).value;
    		if(outVal < 0.0d ) {
    			return false;
    		}
    		totalOut += outVal;
    	}
    	
    	if(totalIn < totalOut) {
    		return false;
    	}
    	
    	return true;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
    	if( possibleTxs == null || possibleTxs.length == 0) {
    		return new Transaction[0];
    	}
    	
    	
    	ArrayList<Transaction> acceptedTxs = new ArrayList<Transaction>();
    	
    	for (Transaction tx : possibleTxs) {
    		if(isValidTx(tx)) {
    			acceptedTxs.add(tx);
    			for (int i = 0; i < tx.getInputs().size(); i++) {
    				Transaction.Input input = tx.getInput(i);
    				UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
    				_utxoPool.removeUTXO(utxo);	
    			}
    			for (int i = 0; i < tx.getOutputs().size(); i++) {
    				Transaction.Output output = tx.getOutput(i);
    				UTXO utxo = new UTXO(tx.getHash(), i);
    				_utxoPool.addUTXO(utxo, output);
    			}    			
    		}
    	} 	
    	
    	return acceptedTxs.toArray(new Transaction[acceptedTxs.size()]);
    }

}
