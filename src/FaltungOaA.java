/***************************************************************************************
 * Copyright (C) 2018 by Sina Steiner. All Rights Reserved.
 * Software is distributed "AS IS" without warranty of any kind,
 * either express or implied. Use at your own risk!
 *
 * This is an ultra basic FaltungOaA. Also, it's not really working, even though I think the principle is implemented correctly. It's probably just way too slow!
 * It uses the basic convolution sum (no FFT or similar things are used) and the overlap and add method.
 *
 * You can load an impulse response (IR) through the 'buffer~' object in the maxpatch. It is then stored in an array by the mxj.
 *
 * The input is written to a buffer padded by (IRLenngth - 1) zeroes on each end. This buffer is then convolved with the IR and the result is written to 1 of 2 output buffers.
 * These output buffers are used one after the other and the first (IRLength - 1) samples of the currently used buffer are added with the (IRLength - 1) last samples of the other
 * buffer (which were calculated previously). These are the samples where, when we calculate the convolution, the IR overlaps with the added zeroes and therefor isn't complete.
 * In this way we should in theory convolve every part of the input singal with the IR. Sadly it doesn't sound quite right yet and there are many things which could cause the problem...
 *
 * One thing is sure though: This is a veeeeeery slow convolver so don't even try to load IR's longer than about 500 samples.
 *
 ****************************************************************************************/


import com.cycling74.max.*;
import com.cycling74.msp.*;

public class FaltungOaA extends MSPPerformer
{
    private int sampleRate;
    private boolean IRLoaded = false;
    private float IRLengthMs, vectorSize;
    private int IRLength, pointerIn = 0, inBufferLength, outBufferLength,
            IRLoadingCounter = 0, outBufferEndPaddingStart, whichBuffer;
    private float[] IR, inBuffer, outSignal;
    private float[][] outBuffer;


    private static final String[] INLET_ASSIST = new String[]{
            "input (sig)", "impulseResponseLength in ms", "IR sample values"
    };
    private static final String[] OUTLET_ASSIST = new String[]{
            "output (sig)", "IR sample Index"
    };


    public FaltungOaA()
    {
        declareInlets(new int[]{SIGNAL, DataTypes.FLOAT, DataTypes.FLOAT});
        declareOutlets(new int[]{SIGNAL, DataTypes.INT});

        setInletAssist(INLET_ASSIST);
        setOutletAssist(OUTLET_ASSIST);
    }
    public void dspsetup(MSPSignal[] ins, MSPSignal[] outs)
    {
		/*Lieber Martin, ich habe herausgefunden, wie man die Vektorgrösse, Samplingrate etc. abfragen kann!
		Es sind Attribute der in- und Outputvektoren. Die dspsetup-Funktion wird jedesmal aufgerufen, wenn das
		Audio im Maxpatch gestartet wird; sie ist also der perfekte Ort um diese Abfrage zu machen.*/
        sampleRate = (int)ins[0].sr;
        vectorSize = ins[0].n;
        post("VectorSize: " + vectorSize);
        post("SampleRate: " + sampleRate);
    }

    /*------------------------Here the impulse response is loaded and initialized------------------*/
    public void inlet(float f)
    {
        if(getInlet() == 1)	//Length of IR is sent to mxj by the info~ object
        {
            if(f <= 0)
            {
                post("Invalid IR");
                return;
            }
            IRLengthMs = f;
            post("IR length in ms: " + IRLengthMs);
            loadIR();
        }

        if(getInlet() == 2)	//Sample values of IR are sent to mxj by the buffer~ object
        {
            IR[IRLoadingCounter] = f;
            IRLoadingCounter++;
            if(IRLoadingCounter >= IRLength)
            {
                IRLoadingCounter = 0;
                IRLoaded = true;
                post("IR loaded");
            }

        }
    }

    public  void loadIR()
    {
        IRLength = (int)(IRLengthMs*sampleRate/1000);		//Transforming ms to samples
        if(IRLength % 2 == 0)
            IRLength -= 1;									//For the overlap-and-add method to work reliably the IR needs to have an uneven samplecount
        IR = new float[IRLength];
        inBufferLength = (int)vectorSize + 2*IRLength - 2;
        outBufferLength = (int)vectorSize+IRLength-1;
        inBuffer = new float[inBufferLength];				//Initializing all the buffer sizes based on the IRLength and the input vector size
        outBuffer = new float[2][outBufferLength];
        outSignal = new float[outBufferLength];

        outBufferEndPaddingStart = outBufferLength - IRLength - 3;	//this is the point in the output buffer, where we need to wait for the next buffer to add its signal

        for(int i = 0; i < IRLength; i++)				// this sends the sample indicies to the buffer~ object, so it start sending the IR sample values
        {
            outlet(1, i);
        }
    }

    public void unloadIR()
    {
        IR = new float[1];
        IRLoaded = false;
        post("IR unloaded");
    }

    /*-------------------------------------------------------------------------------------------------------*/
    public void convolution(int a)
    {
        for(int i = 0; i <outBufferLength; i++)					// we empty the buffer we are going to fill with the result of the convolution
        {
            outBuffer[a][i] = 0;
        }

        int b = (a == 0) ?  1 :  0;									// we assign to be the index of the output buffer we used last time

        for(int m = 0; m < outBufferLength; m++)					//m is the index of output sample
        {

            for(int n = 0; n < IRLength; n++)						//n is the index of the impuls response
            {
                /* The actual convolution.*/
                outBuffer[a][m] += IR[IRLength-1-n]*inBuffer[m+n];
            }

            if(m < (IRLength - 1))									//This is the range where we need to overlap and add
            {
                outSignal[m] = outBuffer[a][m] + outBuffer[b][outBufferEndPaddingStart];
            }
            if(m >= (IRLength - 1) && m < (outBufferEndPaddingStart)) //the samples after m < (outBufferEndPaddingStart) are not yet
            {														  //processed. They will be added to the beginning of the next buffer
                outSignal[m] = outBuffer[a][m];
            }

        }

        whichBuffer = (a == 0) ? 1 : 0;									//Making sure next time the other buffer is used.
    }

    public void perform(MSPSignal[] ins, MSPSignal[] outs)
    {
        int i, outSignalCounter = 0;
        float[] in = ins[0].vec;
        float[] out = outs[0].vec;
        for(i = 0; i < in.length; i++)
        {

            if(IRLoaded)
            {
                inBuffer[i + IRLength - 1] = in[i];									// We write one full input vector to the inBuffer and then process it in the convolution function
                if(i == vectorSize - 1)
                {
                    convolution(whichBuffer);
                    while(outSignalCounter < (outBufferLength - IRLength -1)) 		// making sure the samples which haven't been overlapped and added aren't sent to the output
                    {
                        for(int k = 0; k < out.length; k++)
                        {
                            out[k] = outSignal[outSignalCounter];					// outputting the results
                            if(outSignalCounter < (outBufferLength - IRLength -1))
                                outSignalCounter++;
                            else k = out.length;
                        }
                    }
                }
            }
            if(!IRLoaded) out[i] = in[i];											// If no IR is loaded, the unalterd input is sent to the output.
        }
    }
}