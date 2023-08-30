package com.github.boybeak.xcmpor;

public class FrameDropper {
    private int srcFrameRate;
    private int dstFrameRate;
    private boolean disable;

    private int dropCount;
    private int keepCount;

    public FrameDropper(int srcFrameRate, int dstFrameRate){
        this.srcFrameRate = srcFrameRate;
        this.dstFrameRate = dstFrameRate;
        if(srcFrameRate<=dstFrameRate){
            disable = true;
        }
    }

    public boolean checkDrop(int frameIndex){
        if(disable){
            return false;
        }
        if(frameIndex==0){
            //第一帧保留
            keepCount++;
            return false;
        }
        float targetDropRate = (srcFrameRate-dstFrameRate)/(float)srcFrameRate;
        float ifDropRate = (dropCount+1)/(float)(dropCount+keepCount);
        float ifNotDropRate = (dropCount)/(float)(dropCount+keepCount+1);

        boolean drop = (Math.abs(ifDropRate -targetDropRate) < Math.abs(ifNotDropRate -targetDropRate));

        if(drop){
            dropCount++;
        }else{
            keepCount++;
        }
//        CL.v("目前丢帧率:"+dropCount/(float)(dropCount+keepCount)+" 目标丢帧率:"+targetDropRate);
        return drop;
    }
}
