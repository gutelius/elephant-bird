package com.twitter.elephantbird.pig.load;

import java.io.IOException;

import com.google.protobuf.Message;
import com.twitter.elephantbird.mapreduce.io.ProtobufBlockReader;
import com.twitter.elephantbird.mapreduce.io.ProtobufWritable;
import com.twitter.elephantbird.pig.util.ProtobufToPig;
import com.twitter.elephantbird.util.Protobufs;
import com.twitter.elephantbird.util.TypeRef;
import org.apache.pig.ExecType;
import org.apache.pig.backend.datastorage.DataStorage;
import org.apache.pig.data.Tuple;
import org.apache.pig.impl.logicalLayer.schema.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class LzoProtobufBlockPigLoader<M extends Message> extends LzoBaseLoadFunc {
  private static final Logger LOG = LoggerFactory.getLogger(LzoProtobufBlockPigLoader.class);

  private ProtobufBlockReader<M> reader_ = null;
  private ProtobufWritable<M> value_ = null;
  private TypeRef<M> typeRef_ = null;
  private final ProtobufToPig protoToPig_ = new ProtobufToPig();

  protected enum LzoProtobufBlockPigLoaderCounters { ProtobufsRead }

  public LzoProtobufBlockPigLoader() {
    LOG.info("LzoProtobufBlockLoader zero-parameter creation");
  }

  /**
   * Set the type parameter so it doesn't get erased by Java.  Must be called before getNext!
   *
   * @param typeRef
   */
  public void setTypeRef(TypeRef<M> typeRef) {
    typeRef_ = typeRef;
    value_ = new ProtobufWritable<M>(typeRef_);
  }

  @Override
  public void postBind() throws IOException {
    reader_ = new ProtobufBlockReader<M>(is_, typeRef_);
  }

  @Override
  public void skipToNextSyncPoint(boolean atFirstRecord) throws IOException {
    // We want to explicitly not do any special syncing here, because the reader_
    // handles this automatically.
  }

  @Override
  protected boolean verifyStream() throws IOException {
    return is_ != null;
  }


  /**
   * Return every non-null line as a single-element tuple to Pig.
   */
  public Tuple getNext() throws IOException {
    if (!verifyStream()) {
      return null;
    }

    // If we are past the end of the file split, tell the reader not to read any more new blocks.
    // Then continue reading until the last of the reader's already-parsed values are used up.
    // The next split will start at the next sync point and no records will be missed.
    if (is_.getPosition() > end_) {
      reader_.markNoMoreNewBlocks();
    }

    Tuple t = null;
    if (reader_.readProtobuf(value_)) {
      t = protoToPig_.toTuple(value_.get());
      incrCounter(LzoProtobufBlockPigLoaderCounters.ProtobufsRead, 1L);
    }
    return t;
  }

  @Override
  public Schema determineSchema(String filename, ExecType execType, DataStorage store) throws IOException {
    return protoToPig_.toSchema(Protobufs.getMessageDescriptor(typeRef_.getRawClass()));
  }
}
