// AUTOGENERATED FILE - DO NOT MODIFY!
// This file generated by Djinni from ethereum_like_wallet.djinni

package co.ledger.core;

import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

/**Class representing a Ethereum transaction */
public abstract class EthereumLikeTransaction {
    /** Get the hash of the transaction. */
    public abstract String getHash();

    /** Get the nonce of the transaction : sequence number issued by originating EOA */
    public abstract int getNonce();

    /** Get Gas price (in wei) */
    public abstract Amount getGasPrice();

    /** Get start gas (in wei) : maximum amount of gas the originator is willing to pay */
    public abstract Amount getGasLimit();

    /** Effective used gas */
    public abstract Amount getGasUsed();

    /** Get destination ETH address */
    public abstract EthereumLikeAddress getReceiver();

    /** Get ETH sender address */
    public abstract EthereumLikeAddress getSender();

    /** Get amount of ether to send */
    public abstract Amount getValue();

    /** Get binary data payload */
    public abstract byte[] getData();

    /** Serialize the transaction to its raw format. */
    public abstract byte[] serialize();

    /** Set signature of transaction, when a signature is set serialize method gives back serialized Tx */
    public abstract void setSignature(byte[] rSignature, byte[] sSignature);

    public abstract void setDERSignature(byte[] signature);

    /**
     * Get the time when the transaction was issued or the time of the block including
     * this transaction
     */
    public abstract Date getDate();

    /** Get block to which transaction belongs (was mined in) */
    public abstract EthereumLikeBlock getBlock();

    private static final class CppProxy extends EthereumLikeTransaction
    {
        private final long nativeRef;
        private final AtomicBoolean destroyed = new AtomicBoolean(false);

        private CppProxy(long nativeRef)
        {
            if (nativeRef == 0) throw new RuntimeException("nativeRef is zero");
            this.nativeRef = nativeRef;
        }

        private native void nativeDestroy(long nativeRef);
        public void destroy()
        {
            boolean destroyed = this.destroyed.getAndSet(true);
            if (!destroyed) nativeDestroy(this.nativeRef);
        }
        protected void finalize() throws java.lang.Throwable
        {
            destroy();
            super.finalize();
        }

        @Override
        public String getHash()
        {
            assert !this.destroyed.get() : "trying to use a destroyed object";
            return native_getHash(this.nativeRef);
        }
        private native String native_getHash(long _nativeRef);

        @Override
        public int getNonce()
        {
            assert !this.destroyed.get() : "trying to use a destroyed object";
            return native_getNonce(this.nativeRef);
        }
        private native int native_getNonce(long _nativeRef);

        @Override
        public Amount getGasPrice()
        {
            assert !this.destroyed.get() : "trying to use a destroyed object";
            return native_getGasPrice(this.nativeRef);
        }
        private native Amount native_getGasPrice(long _nativeRef);

        @Override
        public Amount getGasLimit()
        {
            assert !this.destroyed.get() : "trying to use a destroyed object";
            return native_getGasLimit(this.nativeRef);
        }
        private native Amount native_getGasLimit(long _nativeRef);

        @Override
        public Amount getGasUsed()
        {
            assert !this.destroyed.get() : "trying to use a destroyed object";
            return native_getGasUsed(this.nativeRef);
        }
        private native Amount native_getGasUsed(long _nativeRef);

        @Override
        public EthereumLikeAddress getReceiver()
        {
            assert !this.destroyed.get() : "trying to use a destroyed object";
            return native_getReceiver(this.nativeRef);
        }
        private native EthereumLikeAddress native_getReceiver(long _nativeRef);

        @Override
        public EthereumLikeAddress getSender()
        {
            assert !this.destroyed.get() : "trying to use a destroyed object";
            return native_getSender(this.nativeRef);
        }
        private native EthereumLikeAddress native_getSender(long _nativeRef);

        @Override
        public Amount getValue()
        {
            assert !this.destroyed.get() : "trying to use a destroyed object";
            return native_getValue(this.nativeRef);
        }
        private native Amount native_getValue(long _nativeRef);

        @Override
        public byte[] getData()
        {
            assert !this.destroyed.get() : "trying to use a destroyed object";
            return native_getData(this.nativeRef);
        }
        private native byte[] native_getData(long _nativeRef);

        @Override
        public byte[] serialize()
        {
            assert !this.destroyed.get() : "trying to use a destroyed object";
            return native_serialize(this.nativeRef);
        }
        private native byte[] native_serialize(long _nativeRef);

        @Override
        public void setSignature(byte[] rSignature, byte[] sSignature)
        {
            assert !this.destroyed.get() : "trying to use a destroyed object";
            native_setSignature(this.nativeRef, rSignature, sSignature);
        }
        private native void native_setSignature(long _nativeRef, byte[] rSignature, byte[] sSignature);

        @Override
        public void setDERSignature(byte[] signature)
        {
            assert !this.destroyed.get() : "trying to use a destroyed object";
            native_setDERSignature(this.nativeRef, signature);
        }
        private native void native_setDERSignature(long _nativeRef, byte[] signature);

        @Override
        public Date getDate()
        {
            assert !this.destroyed.get() : "trying to use a destroyed object";
            return native_getDate(this.nativeRef);
        }
        private native Date native_getDate(long _nativeRef);

        @Override
        public EthereumLikeBlock getBlock()
        {
            assert !this.destroyed.get() : "trying to use a destroyed object";
            return native_getBlock(this.nativeRef);
        }
        private native EthereumLikeBlock native_getBlock(long _nativeRef);
    }
}