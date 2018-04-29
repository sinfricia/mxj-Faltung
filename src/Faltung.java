import com.cycling74.max.*;
import com.cycling74.msp.*;

public class Faltung extends MSPPerformer
{
	private AudioFileBuffer IR;
	private boolean IRLoaded = false;
	private String IRPath;
	private int pointerIn, pointerOut, IRLength, inBufferLength;
	private float[] inBuffer;


	private static final String[] INLET_ASSIST = new String[]{
		"input (sig)"
	};
	private static final String[] OUTLET_ASSIST = new String[]{
		"output (sig)"
	};


	public Faltung()
	{
		declareInlets(new int[]{SIGNAL});
		declareOutlets(new int[]{SIGNAL});

		setInletAssist(INLET_ASSIST);
		setOutletAssist(OUTLET_ASSIST);
	}

	public void loadIR(String fileName)
	{
		IRPath = MaxSystem.openDialog();
		try {
			IR = new AudioFileBuffer(IRPath);
			post("IR length: " + IR.getFrameLength() + " samples/" + IR.getLengthMs() + "ms");
		}
		catch(Exception e) {
			error("mxj Faltung: Sorry, file was not found");
			return;
		}

		IRLength = (int)IR.getFrameLength();
		inBufferLength = IRLength + 1;
		inBuffer = new float[inBufferLength];
		IRLoaded = true;
		post("IR loaded");
	}

	public void unloadIR()
	{
		IR = null;
		IRLoaded = false;
		post("IR unloaded");
	}

	public void perform(MSPSignal[] ins, MSPSignal[] outs)
	{
		float[] in = ins[0].vec;
		float[] out = outs[0].vec;
		for(int i = 0; i < in.length;i++)
		{
			if(IRLoaded)
			{
				inBuffer[pointerIn] = in[i];
				pointerIn++;
				if(pointerIn >= inBufferLength) pointerIn -= inBufferLength;

				out[i] = 0;
				for(int j = 0; j < IRLength; j++)
				{
					pointerOut = i - j;
					if(pointerOut < 0) pointerOut += inBufferLength;
					out[i] += IR.buf[0][j]*inBuffer[pointerOut];
				}
			}
			else out[i] = in[i];
		}
	}
}

