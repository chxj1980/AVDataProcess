
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Formatter;

public class H264Parser {
    
    //0x00000001��0x000001��һ��nalu����ʼ��־��������һ���˱�־ʱΪ��nalu�Ľ�β��
    //��ʼ��־�ĺ����һ���ֽڣ�type���������nalu�����ͣ�type & 0x1F��Ϊ��nalu�����ͣ�nalu_type����

    private static final int NALU_TYPE_SLICE    = 1;  //��IDRͼ���в��������ݻ��ֵ�Ƭ�Σ�ΪP��B֡
    private static final int NALU_TYPE_DPA      = 2;  //��IDRͼ����A�����ݻ���Ƭ��
    private static final int NALU_TYPE_DPB      = 3;  //��IDRͼ����B�����ݻ���Ƭ��
    private static final int NALU_TYPE_DPC      = 4;  //��IDRͼ����C�����ݻ���Ƭ��
    private static final int NALU_TYPE_IDR      = 5;  //IDRͼ���Ƭ�Σ��ؼ�֡��I֡
    private static final int NALU_TYPE_SEI      = 6;  //������ǿ��Ϣ
    private static final int NALU_TYPE_SPS      = 7;  //���в�������Sequence Parameter Set��
    private static final int NALU_TYPE_PPS      = 8;  //ͼ�������PPS��Picture Parameter Set��
    private static final int NALU_TYPE_AUD      = 9;  //�ָ��
    private static final int NALU_TYPE_EOSEQ    = 10; //���н�����
    private static final int NALU_TYPE_EOSTREAM = 11; //��������
    private static final int NALU_TYPE_FILL     = 12; //�������
    
    private static final int NALU_PRIORITY_DISPOSABLE = 0;
    private static final int NALU_PRIORITY_LOW        = 1;
    private static final int NALU_PRIORITY_HIGH       = 2;
    private static final int NALU_PRIORITY_HIGHEST    = 3;

    private boolean mFirstFind = true;  //��һ�β�����ʼ��
    private int mStartCodeLen = 0;      //��ʼ�볤��
    private int mCurStartCodeLen = 0;
    private int mFrameLen = 0;          //֡����
    private int mFrameFirstByte = 0;    //֡�����еĵ�һ���ֽ�
    private int mCurFrameFirstByte = 0;
    private int mPos = 0;
    private int mLen = 0;
    private String mStartCode = "";
    
    private int mNaluType = 0;
    private int mForbiddenBit = 0;
    private int mNalReferenceIdc = 0;
    
    public static void main(String[] args) {
        if (args.length > 0) {
            H264Parser h264Parser = new H264Parser();
            h264Parser.ParseH264(args[0]);
        } else {
            System.out.println("Missing h264 filename.");
        }
    }
    
    //���NALU��Ӧ��SliceΪһ֡�Ŀ�ʼ����0x00000001(��4���ֽڳ���)���������0x000001(��3���ֽڳ���)��
    public int FindStartCode(InputStream inputStream) {
        
        byte[] startCode = new byte[4];
        int len = -1;
        
        try {
            len = inputStream.read(startCode);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        if (len == 3) {
            if (startCode[0]==0 && startCode[1]==0 && startCode[2]==1) {
                len = 3;
            } else {
                len = 0;
            }
        } else if (len == 4) {
            if (startCode[0]==0 && startCode[1]==0 && startCode[2]==0 && startCode[3]==1) {
                len = 4;
            } else {
                len = 0;
            }
        }
        
        return len;
    }
    
    public int FindStartCode(List<Integer> startCodeList) {

        int len = startCodeList.size();
        
        if (len == 3) {
            if (startCodeList.get(0)==0 && startCodeList.get(1)==0 && startCodeList.get(2)==1) {
                return 3;
            }
        } else if (len == 4) {
            if (startCodeList.get(0)==0 && startCodeList.get(1)==0 && startCodeList.get(2)==0 && startCodeList.get(3)==1) {
                return 4;
            } else if (startCodeList.get(0)==0 && startCodeList.get(1)==0 && startCodeList.get(2)==1) {
                return 3;
            }
        }
        return 0;
    }
    
    public int GetNalu(InputStream inputStream) {
        mPos = mPos + mLen;
        
        //��һ�β�����ʼ�룬ֻ��Ϊ���ƶ��ļ�ָ��
        if (mFirstFind) {
            mFirstFind = false;
            if (-1 == (mStartCodeLen = FindStartCode(inputStream))) {
                return -1;
            }
        }
        
        if (mStartCodeLen == 3) {
            mStartCode = "001";
        } else {
            mStartCode = "0001";
        }
        
        List<Integer> startCodeList = new ArrayList<>();
        int frameLen = 0;
        while(true) {
            int tmp = -1;
            
            //һ���ֽ�һ���ֽڴ��ļ����ж�ȡ
            try {
                tmp = inputStream.read();
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            //��ȡ���ļ�β
            if (tmp == -1) {
                mFrameLen = frameLen;
                return -1;
            }
            
            //֡���ݳ����ۼ�
            frameLen++;
            
            //�����ļ��ж�ȡ�����ݱ��浽list��
            startCodeList.add(tmp);
            
            //��һ֡�ĵ�һ���ֽ��л�ȡNALU��Ϣ
            if (frameLen == 1) {
                //�����һ�β��ҵ�����ʼ�볤����3�����ϴ��Ѿ������˵�һ���ֽڵ�����
                if (mStartCodeLen == 3) {
                    tmp = mFrameFirstByte;
                    frameLen++; //֡���ȼ��ϵ�һ���ֽ�
                }
                mCurFrameFirstByte = tmp;
                mForbiddenBit      = tmp & 0x80;
                mNalReferenceIdc   = tmp & 0x60;
                mNaluType          = tmp & 0x1f;
            }
            
            //�����ĸ��ֽڵ�list��
            if (startCodeList.size() < 4) {
                continue;
            }
            
            //������ʼ�룬�������ֵ����0�ͱ�ʾ�ҵ���
            int startCodeLen = FindStartCode(startCodeList);
            if (startCodeLen > 0) {
                if (startCodeLen == 3) {
                    mFrameFirstByte = tmp;
                }
                frameLen = frameLen - 4;
                mLen = frameLen + mStartCodeLen;
                mStartCodeLen = startCodeLen;
                break;
            }
            startCodeList.remove(0);
        }
        mFrameLen = frameLen;
        return 0;
    }
    
    public boolean ParseH264(String fileName) {
        if (fileName.equals("")) {
            return false;
        }
        InputStream inputStream = null;
        try {
            File file = new File(fileName);
            inputStream = new FileInputStream(file);
        } catch (Exception e) {
            e.printStackTrace();
            inputStream = null;
        }
        if (inputStream == null) {
            return false;
        }
        
        System.out.println("-----+---------+----- NALU Table -+---------+------------+");  
        System.out.println(" NUM |   POS   |   IDC   |  TYPE  |   LEN   | START_CODE |");  
        System.out.println("-----+---------+---------+--------+---------+------------+"); 
        
        int iNaluNum = 0;
        while(true) {
            int dataLen = GetNalu(inputStream);
            
            iNaluNum++;
            String sNaluType = "";
            switch(mNaluType) {
                case NALU_TYPE_SLICE:    sNaluType = "SLICE";    break;
                case NALU_TYPE_DPA:      sNaluType = "DPA";      break;
                case NALU_TYPE_DPB:      sNaluType = "DPB";      break;
                case NALU_TYPE_DPC:      sNaluType = "DPC";      break;
                case NALU_TYPE_IDR:      sNaluType = "IDR";      break;
                case NALU_TYPE_SEI:      sNaluType = "SEI";      break;
                case NALU_TYPE_SPS:      sNaluType = "SPS";      break;
                case NALU_TYPE_PPS:      sNaluType = "PPS";      break;
                case NALU_TYPE_AUD:      sNaluType = "AUD";      break;
                case NALU_TYPE_EOSEQ:    sNaluType = "EOSEQ";    break;
                case NALU_TYPE_EOSTREAM: sNaluType = "EOSTREAM"; break;
                case NALU_TYPE_FILL:     sNaluType = "FILL";     break;
            }
            String sIdc = "";
            switch(mNalReferenceIdc >> 5) {
                case NALU_PRIORITY_DISPOSABLE:sIdc = "DISPOS"; break;  
                case NALU_PRIORITY_LOW:       sIdc = "LOW";    break;  
                case NALU_PRIORITY_HIGH:      sIdc = "HIGH";   break;  
                case NALU_PRIORITY_HIGHEST:   sIdc = "HIGHEST";break; 
            }
            Formatter formatter = new Formatter(System.out);
            formatter.format("%5d| %8d| %8s| %7s| %8d| %11s|\n", iNaluNum, mPos, sIdc, sNaluType, mFrameLen, mStartCode);
            if (dataLen == -1) {
                break;
            }
        }
        
        try {
            inputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }
}