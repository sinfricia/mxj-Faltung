import com.cycling74.max.*;
import com.cycling74.msp.*;

public class Faltung extends MSPPerformer
{
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

    public void inlet(float f)
    {

    }

    public void dspsetup(MSPSignal[] ins, MSPSignal[] outs)
    {
        //If you forget the fields of MSPSignal you can select the classname above
        //and choose Open Class Reference For Selected Class.. from the Java menu
    }

    public void perform(MSPSignal[] ins, MSPSignal[] outs)
    {

        int i;
        float[] in = ins[0].vec;
        float[] out = outs[0].vec;
        for(i = 0; i < in.length;i++)
        {
            out[i] = in[i];

        }
    }
}