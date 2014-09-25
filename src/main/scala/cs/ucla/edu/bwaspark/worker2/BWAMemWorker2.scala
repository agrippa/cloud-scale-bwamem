package cs.ucla.edu.bwaspark.worker2

import cs.ucla.edu.bwaspark.datatype._
import cs.ucla.edu.bwaspark.worker2.MemMarkPrimarySe._
import cs.ucla.edu.bwaspark.worker2.MemRegToADAMSAM._
import cs.ucla.edu.bwaspark.worker2.MemSamPe.{memSamPe, memSamPeGroup, memSamPeGroupJNI}
import cs.ucla.edu.avro.fastq._

object BWAMemWorker2 {
  private val MEM_F_PE: Int = 0x2

  /**
    *  Main function of BWA-mem worker2
    *
    *  @param opt the input MemOptType object
    *  @param regs the alignment registers to be transformed
    *  @param bns the input BNSSeqType object
    *  @param pac the PAC array
    *  @param seq the read (NOTE: currently we use Array[Byte] first. may need to be changed!!!)
    */
  def bwaMemWorker2(opt: MemOptType, regs: Array[MemAlnRegType], bns: BNTSeqType, pac: Array[Byte], seq: FASTQRecord, numProcessed: Long) {
    var regsOut: Array[MemAlnRegType] = null
    if(regs != null)
      regsOut = memMarkPrimarySe(opt, regs, numProcessed)

    
    //pre-process: transform A/C/G/T to 0,1,2,3

    def locusEncode(locus: Char): Byte = {
      //transforming from A/C/G/T to 0,1,2,3
      locus match {
        case 'A' => 0
        case 'a' => 0
        case 'C' => 1
        case 'c' => 1
        case 'G' => 2
        case 'g' => 2
        case 'T' => 3
        case 't' => 3
        case '-' => 5
        case _ => 4
      }
    }

    val seqStr = new String(seq.getSeq.array)
    val seqTrans: Array[Byte] = seqStr.toCharArray.map(ele => locusEncode(ele))

    memRegToSAMSe(opt, bns, pac, seq, seqTrans, regsOut, 0, null)
  }


  def pairEndBwaMemWorker2(opt: MemOptType, bns: BNTSeqType, pac: Array[Byte], numProcessed: Long, pes: Array[MemPeStat], pairEndRead: PairEndReadType) {
    var alnRegVec: Array[Array[MemAlnRegType]] = new Array[Array[MemAlnRegType]](2)
    var seqs: PairEndFASTQRecord = new PairEndFASTQRecord
    seqs.seq0 = pairEndRead.seq0
    seqs.seq1 = pairEndRead.seq1
    alnRegVec(0) = pairEndRead.regs0
    alnRegVec(1) = pairEndRead.regs1
    memSamPe(opt, bns, pac, pes, numProcessed, seqs, alnRegVec)
  }


  def pairEndBwaMemWorker2PSWBatched(opt: MemOptType, bns: BNTSeqType, pac: Array[Byte], numProcessed: Long, pes: Array[MemPeStat], 
                                     pairEndReadArray: Array[PairEndReadType], subBatchSize: Int, isPSWJNI: Boolean, jniLibPath: String) {
    var alnRegVecPairs: Array[Array[Array[MemAlnRegType]]] = new Array[Array[Array[MemAlnRegType]]](subBatchSize)
    var seqsPairs: Array[PairEndFASTQRecord] = new Array[PairEndFASTQRecord](subBatchSize)

    var i = 0
    while(i < subBatchSize) {
      alnRegVecPairs(i) = new Array[Array[MemAlnRegType]](2)
      seqsPairs(i) = new PairEndFASTQRecord
      seqsPairs(i).seq0 = pairEndReadArray(i).seq0
      seqsPairs(i).seq1 = pairEndReadArray(i).seq1
      alnRegVecPairs(i)(0) = pairEndReadArray(i).regs0
      alnRegVecPairs(i)(1) = pairEndReadArray(i).regs1
      i += 1
    }

    if(isPSWJNI) {
      System.load(jniLibPath)
      memSamPeGroupJNI(opt, bns, pac, pes, subBatchSize, numProcessed, seqsPairs, alnRegVecPairs, false, null)
    }
    else
      memSamPeGroup(opt, bns, pac, pes, subBatchSize, numProcessed, seqsPairs, alnRegVecPairs, false, null)
  }

  
  def pairEndBwaMemWorker2PSWBatchedSAMRet(opt: MemOptType, bns: BNTSeqType, pac: Array[Byte], numProcessed: Long, pes: Array[MemPeStat], 
                                           pairEndReadArray: Array[PairEndReadType], subBatchSize: Int, isPSWJNI: Boolean, jniLibPath: String): Array[Array[String]] = {
    var alnRegVecPairs: Array[Array[Array[MemAlnRegType]]] = new Array[Array[Array[MemAlnRegType]]](subBatchSize)
    var seqsPairs: Array[PairEndFASTQRecord] = new Array[PairEndFASTQRecord](subBatchSize)
    var samStringArray: Array[Array[String]] = new Array[Array[String]](subBatchSize) // return SAM string

    var i = 0
    while(i < subBatchSize) {
      alnRegVecPairs(i) = new Array[Array[MemAlnRegType]](2)
      samStringArray(i) = new Array[String](2)
      seqsPairs(i) = new PairEndFASTQRecord
      seqsPairs(i).seq0 = pairEndReadArray(i).seq0
      seqsPairs(i).seq1 = pairEndReadArray(i).seq1
      alnRegVecPairs(i)(0) = pairEndReadArray(i).regs0
      alnRegVecPairs(i)(1) = pairEndReadArray(i).regs1
      samStringArray(i)(0) = new String
      samStringArray(i)(1) = new String
      i += 1
    }

    if(isPSWJNI) {
      System.load(jniLibPath)
      memSamPeGroupJNI(opt, bns, pac, pes, subBatchSize, numProcessed, seqsPairs, alnRegVecPairs, true, samStringArray)
    }
    else
      memSamPeGroup(opt, bns, pac, pes, subBatchSize, numProcessed, seqsPairs, alnRegVecPairs, true, samStringArray)

    samStringArray
  }
}
