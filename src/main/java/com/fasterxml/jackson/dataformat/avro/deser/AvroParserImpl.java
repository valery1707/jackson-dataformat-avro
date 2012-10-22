package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DecoderFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.dataformat.avro.AvroParser;
import com.fasterxml.jackson.dataformat.avro.AvroReadContext;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;

/**
 * Implementation class that exposes additional internal API
 * to be used as callbacks by {@link AvroReadContext} implementations.
 */
public class AvroParserImpl extends AvroParser
{
    protected final static byte[] NO_BYTES = new byte[0];
    
    protected final BinaryDecoder _decoder;

    protected ByteBuffer _byteBuffer;
    
    public AvroParserImpl(IOContext ctxt, int parserFeatures, int avroFeatures,
            ObjectCodec codec, InputStream in)
    {
        super(ctxt, parserFeatures, avroFeatures, codec, in);
        _decoder = DecoderFactory.get().binaryDecoder(in, null);
    }

    public AvroParserImpl(IOContext ctxt, int parserFeatures, int avroFeatures,
            ObjectCodec codec,
            byte[] data, int offset, int len)
    {
        super(ctxt, parserFeatures, avroFeatures, codec,
                data, offset, len);
        _decoder = DecoderFactory.get().binaryDecoder(data, offset, len, null);
    }

    /*
    /**********************************************************
    /* Abstract method impls
    /**********************************************************
     */

    @Override
    public JsonToken nextToken() throws IOException, JsonParseException
    {
        _binaryValue = null;
        if (_closed) {
            return null;
        }
        JsonToken t = _avroContext.nextToken();
        if (t != null) { // usual quick case
            _currToken = t;
            return t;
        }
        // Otherwise, maybe context was closed
        while (true) {
            AvroReadContext ctxt = _avroContext.getParent();
            if (ctxt == null)  { // root context, end!
                _currToken = null;
                close();
                return null;
            }
            _avroContext = ctxt;
            t = ctxt.nextToken();
            if (t != null) {
                _currToken = t;
                return t;
            }
        }
    }
    
    @Override
    protected void _initSchema(AvroSchema schema)
    {
        try {
            _avroContext = new RootContext(this, _decoder, schema.getAvroSchema());
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
    
    /*
    /**********************************************************
    /* Methods for AvroReadContext implementations
    /**********************************************************
     */

    protected void setAvroContext(AvroReadContext ctxt) {
        _avroContext = ctxt;
    }

    protected ByteBuffer borrowByteBuffer() {
        return _byteBuffer;
    }
    
    protected void setBytes(ByteBuffer bb)
    {
        int len = bb.remaining();
        if (len <= 0) {
            _binaryValue = NO_BYTES;
            return;
        }
        _binaryValue = new byte[len];
        bb.get(_binaryValue);
        // plus let's retain reference to this buffer, for reuse
        // (is safe due to way Avro impl handles them)
        _byteBuffer = bb;
    }

    protected JsonToken setNumber(int v) {
        _numberInt = v;
        _numTypesValid = NR_INT;
        return JsonToken.VALUE_NUMBER_INT;
    }

    protected JsonToken setNumber(long v) {
        _numberLong = v;
        _numTypesValid = NR_LONG;
        return JsonToken.VALUE_NUMBER_INT;
    }

    protected JsonToken setNumber(float v) {
        _numberDouble = v;
        _numTypesValid = NR_DOUBLE;
        return JsonToken.VALUE_NUMBER_FLOAT;
    }

    protected JsonToken setNumber(double v) {
        _numberDouble = v;
        _numTypesValid = NR_DOUBLE;
        return JsonToken.VALUE_NUMBER_FLOAT;
    }

    protected JsonToken setString(String str) {
        _textValue = str;
        return JsonToken.VALUE_STRING;
    }
}
