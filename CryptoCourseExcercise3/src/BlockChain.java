/**
 * @author Toni Schmidt
 */

import java.util.ArrayList;

public class BlockChain {

	public class BlockNode {
		public Block getBlock() {
			return block;
		}

		public void setBlock(Block block) {
			this.block = block;
		}

		public long getTimestamp() {
			return timestamp;
		}

		public void setTimestamp(long timestamp) {
			this.timestamp = timestamp;
		}

		public int getHeight() {
			return height;
		}

		public void setHeight(int height) {
			this.height = height;
		}

		public ArrayList<BlockNode> getChildren() {
			return children;
		}

		public void setChildren(ArrayList<BlockNode> children) {
			this.children = children;
		}

		private Block block;
		private long timestamp;
		private int height;
		private ArrayList<BlockNode> children;
		private UTXOPool utxoPool;
		private ByteArrayWrapper hash;

		public ByteArrayWrapper getHash() {
			return hash;
		}

		public void setHash(ByteArrayWrapper hash) {
			this.hash = hash;
		}

		public UTXOPool getUtxoPool() {
			return utxoPool;
		}

		public void setUtxoPool(UTXOPool utxoPool) {
			this.utxoPool = utxoPool;
		}

		public BlockNode(Block block, UTXOPool utxoPool, int height, long timestamp) {
			this.utxoPool = utxoPool;
			this.height = height;
			this.timestamp = timestamp;
			this.block = block;
			this.hash = new ByteArrayWrapper(block.getHash());
			children = new ArrayList<BlockNode>();
		}

		public void addChild(BlockNode blockNode) {
			this.children.add(blockNode);
		}

	}

	public static final int CUT_OFF_AGE = 30;

	private TransactionPool _transactionPool;

	private BlockNode root;
	private BlockNode maxHeightNode;

	UTXOPool dummyPool;
	Block dummyBlock;
	
/**
 * Tree traversal to find the parent of a block	
 * @param startNode Where to start the traversal. If null, traversal will be started at the Genesis block
 * @param parentHash The hash of the block's parent
 * @return The block with hash parentHash
 */
	private BlockNode findParent(BlockNode startNode, ByteArrayWrapper parentHash) {
		BlockNode ret = null;
		if (startNode == null) {
			startNode = root;
		}
		if (startNode.getHash().equals(parentHash)) {
			ret = startNode;
		} else if (startNode.getChildren().size() == 0) {
			return null;
		} else {
			for (BlockNode bn : startNode.getChildren()) {
				ret = findParent(bn, parentHash);
				if (ret != null) {
					return ret;
				}
			}
		}
		return ret;
	}	

	/**
	 * create an empty block chain with just a genesis block. Assume
	 * {@code genesisBlock} is a valid block
	 */
	public BlockChain(Block genesisBlock) {
		UTXOPool initialPool = new UTXOPool();
		addCoinbaseToUTXOPool(genesisBlock, initialPool);
		_transactionPool = new TransactionPool();
		root = new BlockNode(genesisBlock, initialPool, 1, System.currentTimeMillis());
		maxHeightNode = root;
	}

	public int getMaxHeight() {
		return maxHeightNode.getHeight();
	}

	/** Get the maximum height block */
	public Block getMaxHeightBlock() {
		return maxHeightNode.getBlock();
	}

	/** Get the UTXOPool for mining a new block on top of max height block */
	public UTXOPool getMaxHeightUTXOPool() {
		return maxHeightNode.getUtxoPool();
	}

	/** Get the transaction pool to mine a new block */
	public TransactionPool getTransactionPool() {
		return _transactionPool;
	}


	/**
	 * Add {@code block} to the block chain if it is valid. For validity, all
	 * transactions should be valid and block should be at
	 * {@code height > (maxHeight - CUT_OFF_AGE)}.
	 * 
	 * <p>
	 * For example, you can try creating a new block over the genesis block (block
	 * height 2) if the block chain height is {@code <=
	 * CUT_OFF_AGE + 1}. As soon as {@code height > CUT_OFF_AGE + 1}, you cannot
	 * create a new block at height 2.
	 * 
	 * @return true if block is successfully added
	 */
	public boolean addBlock(Block block) {
		if (block == null) {
			return false;
		}

		if (block.getPrevBlockHash() == null) {
			return false;
		}

		ByteArrayWrapper parentHash = new ByteArrayWrapper(block.getPrevBlockHash());
		
		// Find parent Block Node
		BlockNode parentNode = findParent(null, parentHash);

		// If no parent in the chain, return
		if (parentNode == null) {
			return false;
		}

		
		int prevHeight = parentNode.getHeight();
		int newHeight = prevHeight + 1;
		int maxHeight = getMaxHeight();
		
		// Only add the block if its parent is not too old
		if (newHeight < maxHeight - CUT_OFF_AGE + 1) {
			return false;
		}

		UTXOPool utxoPool = parentNode.getUtxoPool();
		int numTxIn = block.getTransactions().size();
		Transaction[] blockTxs = block.getTransactions().toArray(new Transaction[numTxIn]);

		// Copy the parent's UTXOPool, for usage with the new block
		UTXOPool newPool = new UTXOPool(utxoPool);
		TxHandler txH = new TxHandler(newPool);
		
		// Check if all transactions are valid
		Transaction[] performedTxs = txH.handleTxs(blockTxs);
		if (performedTxs.length != numTxIn) {
			return false;
		}
		
		// Perform transactions
		for (Transaction tx : performedTxs) {
			for (int i = 0; i < tx.getInputs().size(); i++) {
				Transaction.Input input = tx.getInput(i);
				UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
				newPool.removeUTXO(utxo);
			}
			for (int i = 0; i < tx.getOutputs().size(); i++) {
				Transaction.Output output = tx.getOutput(i);
				UTXO utxo = new UTXO(tx.getHash(), i);
				newPool.addUTXO(utxo, output);
			}
		}
		
		// Remove performed transactions from pool
		for (Transaction tx : performedTxs) {
			Transaction txInPool = _transactionPool.getTransaction(tx.getHash());
			if (txInPool != null) {
				_transactionPool.removeTransaction(tx.getHash());
			}
		}
		
		addCoinbaseToUTXOPool(block, newPool);
		
		// Add new block node and add to chain
		BlockNode newNode = new BlockNode(block, newPool, newHeight, System.currentTimeMillis());
		parentNode.addChild(newNode);
		if (newHeight > getMaxHeight()) {
			maxHeightNode = newNode;
		}

		return true;

	}

	private void addCoinbaseToUTXOPool(Block block, UTXOPool utxoPool) {
		Transaction coinbase = block.getCoinbase();
		for (int i = 0; i < coinbase.numOutputs(); i++) {
			Transaction.Output out = coinbase.getOutput(i);
			UTXO utxo = new UTXO(coinbase.getHash(), i);
			utxoPool.addUTXO(utxo, out);
		}
	}

	/** Add a transaction to the transaction pool */
	public void addTransaction(Transaction tx) {
		_transactionPool.addTransaction(tx);
	}
}