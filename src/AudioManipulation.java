 /**
 * AudioManipulation.java
 *
 * Time-stamp: <2019-01-30 15:41:09 rlc3>
 *
 * Defines mixer/effect functions on audio streams
 * Utilises the AudioInputStream class 
 * 
 * To compile: javac -classpath editor.jar:. RunEffects.java
 * To run use: java -classpath editor.jar:. RunEffects
 * 
 */ 

import javax.sound.sampled.*;
import java.io.*; 
import java.util.*;

public class AudioManipulation {

/**** echo *****************************************************************/

	/**
	 * 
	 * @param ais - AudioInputStream
	 * @param timeDelay - how long off a delay before playing echo
	 * @param fading0 - by how much the copy0 fade
	 * @param fading1 - by how much copy1 fades
	 * @return void - no return
	 * @frameRate
	 * frameRate = sample rate
	 * @frameSize = storage for the signal - needs to be the right size
	 * 
	 */
    public static AudioInputStream echo(AudioInputStream ais, int timeDelay, double fading0, double fading1) {

    
	byte[] a = null;
	int[] data, ch0, ch1;
	int max;

	try{

	    // AudioInputStream methods 
	    int numChannels     = ais.getFormat().getChannels();
	    int sampleSize 	= ais.getFormat().getSampleSizeInBits();
	    boolean isBigEndian = ais.getFormat().isBigEndian();
	    float sampleRate 	= ais.getFormat().getSampleRate();
	    float frameRate 	= ais.getFormat().getFrameRate();
        int frameSize 	= ais.getFormat().getFrameSize(); //2*16/8 = no. of channels * no of bit per channel/8 = width*height/8
	    int frameLength 	= (int) ais.getFrameLength();

            // sampleRate = framerate = 44100.0 Hz (playback rate = sampling rate!) 
	    // 1 sec = 1000 millisecs 
	    // calculate delay in frames 
	    int frameDelay = (int) (timeDelay/1000.0 * frameRate);

	    // reset the AudioInputStream (mark goes to the start) 
	    ais.reset();

	    // create a byte array of the right size
    	    // recall the lecture OHP slides .. 
	    a = new byte[(int) frameLength*frameSize];

	    // fill the byte array with the data of the AudioInputStream
	    ais.read(a);

	    // Create an integer array, data, of the right size
	    // only reason to do this is enabling type double mixing calculations
	    // Each (channel) sample is made of 2 = sampleSize/8 bytes 
	    data = new int[a.length/2];

	    // fill the integer array by combining two 2 = sampleSize/8 bytes per sample of the
	    // byte array a into one integer
	    // Bytes HB and LB Big Endian make up one integer 
 	    for (int i=0; i<data.length; ++i) {
		/* First byte is HB (most significant digits) - coerce to 32-bit int */
		// HB =def sign_extend(a[2*i]) from 8 bit byte to 32 bit int 
		int HB = (int) a[2*i];
		/* Second byte is LB (least significant digits) - coerce to 32-bit int */
		// LB =def sign_extend(a[2*i+1]) from 8 bit byte to 32 bit int 
		int LB = (int) a[2*i+1];
		// note that data[i] =def sign_extend(HB.LB) 
		// | : Bool^32 x Bool^32 -----> Bool^32 where Bool = {0, 1} 
		data[i] =  HB << 8 | (LB & 0xff); 
 	    }

	    // split integer data array samples into two channels
	    // if both channels are faded by the same factor 
	    // then there is no need to split the channels 
	    ch0 = new int[data.length/2];
	    ch1 = new int[data.length/2];
	    for (int i=0; i<data.length/2; i++) {
		ch0[i] = data[2*i];
		ch1[i] = data[2*i+1];
	    }

	    // Adding a faded copy of the early signal to the later signal
	    // THIS IS THE ECHO !!
	    for (int i=frameDelay; i<ch0.length; ++i) {
		ch0[i] += (int) (ch0[i-frameDelay]*fading0); //adds a few a signal frame before stored at ch0[i-frameDelay] to a later frame *fading0
		ch1[i] += (int) (ch1[i-frameDelay]*fading1);
	    }

	    // combine the two channels
	    for (int i=0; i<data.length; i+=2) {
		data[i]   = ch0[i/2];
		data[i+1] = ch1[i/2];
	    }  

	    // get the maximum amplitute
	    max=0;
	    for (int i=0; i<data.length; ++i) {
		max=Math.max(max,Math.abs(data[i]));
	    }

            // 16 digit 2s-complement range from -2^15 to +2^15-1 = 256*128-1
	    // therefore we linearly scale data[i] values to lie within this range .. 
	    // .. so that each data[i] has a 16 digit "HB.LB binary representation" 
	    if (max > 256*128 - 1) {
		System.out.println("Sound values are linearly scaled by " + (256.0*128.0-1)/max + 
             " because maximum amplitude is larger than upper boundary of allowed value range."); 
		for (int i=0; i<data.length; ++i) {
		    data[i] = (int) (data[i]*(256.0*128.0-1)/max);
		}
            }

	    // convert the integer array to a byte array 
	    for (int i=0; i<data.length; ++i) {
		a[2*i] 	  = (byte)  ((data[i] >> 8) & 0xff);
		a[2*i+1]  = (byte)         (data[i] & 0xff);
	    }

	} catch(Exception e){
	    System.out.println("Something went wrong");
	    e.printStackTrace();
	}

	// create a new AudioInputStream out of the the byteArray
	// and return it.
	return new AudioInputStream(new ByteArrayInputStream(a),
				    ais.getFormat(),ais.getFrameLength());
    }

/**** scaleToZero *****************************************************************/

    public static AudioInputStream scaleToZero(AudioInputStream ais) {

	byte[] a = null;
	int[] data, ch0, ch1;
	int max;

	int frameSize 	= ais.getFormat().getFrameSize(); 
    int frameLength 	= (int) ais.getFrameLength();
    
    try {
		ais.reset();
		
		//create byte array 
		a = new byte[(int) frameLength*frameSize];
		
		//fill the byte array with data of the AudioInputStream
		ais.read(a);
		
		//Create integer array, data , of the right size
		// only reason to do this is enabling type float/double mixing calculations
		data = new int [a.length/2];
		
		//fill the integer array by combining two bytes of the
		// byte array a into one integer - see lectures
		for (int i = 0; i < data.length;++i) {
			int HB = (int) a[2*i];
			int LB = (int) a[2*i+1];
			data[i] = HB << 8 | (LB & 0xff);
		}
		
		
		
		// scale data linearly by a factor of 3/4  
	    // **** NB this is the only part of scaleToZero that is not already part of
	    // echo effect !!!! ****
	   
	  
	   
		for (int i = 0; i < data.length ; i +=2) {
		double scaleFactor = (-3.0/4*1/(data.length-2) * i +1);
//	   if (max > 256 *128 - 1) {
		   data [i] = (int) (data[i]*scaleFactor);
		   data [i+1] = (int) (data[i+1]*scaleFactor);
//		   scaleFactor = scaleFactor*scaleFactor;
//		   
//	   }
	   
	   }
		
	   // convert the integer array to a byte array 
//	   ?? for (int i=0; i<data.length; ++i) {
//		a[2*i] 	  = (byte)  ((data[i] >> 8) & 0xff);
//		a[2*i+1]  = (byte)         (data[i] & 0xff);
//	    }
	   
	   
	   for (int i = 0; i < data.length; i++) {
		   a[2*i] = (byte) ((data[i] >> 8) & 0xff);
		   a[2*i+1] = (byte) (data[i]& 0xff);
	   }
	   
	} catch (IOException e) {
		e.printStackTrace();
	}
/* ----- template code commented out BEGIN 

	try{
	
        int frameSize 	= ais.getFormat().getFrameSize(); 
	    int frameLength 	= (int) ais.getFrameLength();

	    // reset the AudioInputStream (mark goes to the start) 
	    ?? 

	    // create a byte array of the right size
    	    // recall the lecture OHP slides .. 
	    ?? 

	    // fill the byte array with the data of the AudioInputStream
	    ?? 

	    // Create an integer array, data, of the right size
	    // only reason to do this is enabling type float/double mixing calculations  
	    ?? 

	    // fill the integer array by combining two bytes of the
	    // byte array a into one integer - see lectures
	    ?? 

	    // scale data linearly by a factor of 3/4  
	    // **** NB this is the only part of scaleToZero that is not already part of
	    // echo effect !!!! ****
	    ?? 

	    // convert the integer array to a byte array 
	    for (int i=0; i<data.length; ++i) {
		a[2*i] 	  = (byte)  ((data[i] >> 8) & 0xff);
		a[2*i+1]  = (byte)         (data[i] & 0xff);
	    }


	} catch(Exception e){
	    System.out.println("Something went wrong");
	    e.printStackTrace();
	}

 ----- template code commented out END */

	// create a new AudioInputStream out of the the byteArray
	// and return it.
	return new AudioInputStream(new ByteArrayInputStream(a),
				    ais.getFormat(),ais.getFrameLength());
    }

/**** addNote *****************************************************************/

    /**
     * 
     * @param ais - audio input stream
     * @param frequency - double
     * @param noteLengthInMilliseconds - int
     * @return
     */
    public static AudioInputStream addNote(AudioInputStream ais,
                                           double frequency,
					   int noteLengthInMilliseconds) {
	byte[] a = null;
	int[] data;
	int frameSize 	= ais.getFormat().getFrameSize(); 
	int numChannels = ais.getFormat().getChannels(); 
	long frameLength = ais.getFrameLength();
	
	
 try {
	 float frameRate = ais.getFormat().getFrameRate();
	 int noteLengthInFrames = (int) (frameRate * (noteLengthInMilliseconds/1000.0)); // don't divide int by int - will lose everything after the dot
	
	 
	 int noteLengthInBytes = noteLengthInFrames*frameSize; //usually 4 = bytes per channel - based on notes
	 int noteLengthInInts = (int) (noteLengthInBytes / numChannels); // size of one channel
	 
	 a = new byte[noteLengthInBytes];
	 data = new int [noteLengthInInts];
	 
	 //ais.read(a);
	 
	 double k = 64*256;//amplitude - given in exercise brief
	 
	// BEFORE "frame" data[i]data[i+1] plays, how many frames are there?
	 for (int i = 0; i < noteLengthInInts; i+=2) {
	// hence compute t in terms of i 
	// double t = ?? 
		double  t = (i/2)  * (1.0/frameRate); // to keep it as a double, else it ends up flat
		// data[i]   = ?? (one line of code) 
	
		data[i] = (int) (k * Math.sin(frequency * (2 * Math.PI) * t ));
		data[i+1] = (int) (k * Math.sin(frequency * (2 * Math.PI) * t ));
		// ?? one more line of code here
	 }
		
		
	 // convert the integer array to a byte array 
	 for (int i=0; i<data.length; ++i) {
			a[2*i] 	  = (byte)  ((data[i] >> 8) & 0xff);
			a[2*i+1]  = (byte)         (data[i] & 0xff);
		    }
	 
	 
	 
 }
 catch (Exception e) {
	 e.printStackTrace();
 }



	return append(new AudioInputStream(new ByteArrayInputStream(a), 
				    ais.getFormat(), a.length/ais.getFormat().getFrameSize()),ais);

    }  // end addNote


/**** append *****************************************************************/

      // THIS METHOD append IS SUPPLIED FOR YOU 
    public static AudioInputStream append(AudioInputStream ais1, AudioInputStream ais2){
		
		byte[] a,b,c = null;
		try {
			a=new byte[(int) ais1.getFrameLength() *
			ais1.getFormat().getFrameSize()];

			// fill the byte array with the data of the AudioInputStream
			ais1.read(a);
			b=new byte[(int) ais2.getFrameLength() *
			ais2.getFormat().getFrameSize()];

			// fill the byte array with the data of the AudioInputStream
			ais2.read(b);
			
			c=new byte[a.length + b.length];
			for (int i=0; i<c.length; i++) {
				if (i<a.length) {
					c[i]=a[i];
				}
				else	
					c[i]=b[i-a.length];
			}
		
		} catch(Exception e){
			System.out.println("Something went wrong");
			e.printStackTrace();
		}
			

        return new AudioInputStream(new ByteArrayInputStream(c),
				    ais1.getFormat(), c.length/ais1.getFormat().getFrameSize());
	} // end append

/**** tune  *****************************************************************/

	public static AudioInputStream tune(AudioInputStream ais){

     		try {
				ais.reset();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
     		byte [] c = new byte[1];
     		AudioInputStream temp = new AudioInputStream(new ByteArrayInputStream(c), ais.getFormat(),0);
     		//notes - some of these will be needed to play the tune
     		double C4	= 261.63;
    		double D4	= 293.66; 	
    		double Eb4  = 311.13;
    		double E4	= 329.63; 	
    		double F4	= 349.23; 	
    		double G4	= 392.00; 	
    		double A4	= 440.00; 	
    		double B4	= 493.88; 
    		double C5	= 523.25; 	
    		double D5	= 587.33; 
    		double Eb5  = 622.25;
    		double F5	= F4*2;
    		double G5	= G4*2;
    		//
    		double E5 	= E4*2;
    		double A5	= A4*2;
    		double B5	= B4*2;
    		
    		//
    		double A6	= A5*2;
    		double B6	= B5*2;
    		double C6 	= C5*2;
    		double D6	= D5*2;
    		double E6	= E5*2;
    		double E6b	= Eb5*2;
    		double F6	= F5*2;
    		double G6	= G5*2;
    		
    		double A7 = A6*2;
    		double B7 = B6*2;
    		double C7 = 2093.00;
    		
    		
    		int s = 500; // the length in milliseconds for each note
    		int l = 2000;
    		int ll = 2500;
    		int lll = 2800;
    		
    		//specfying the tune 
    		double [][] notes = {
    				{C4,l}, {G4,l},{C5,l},
    				{E5,s},{Eb5,lll},
    				{C4,l}, {G4,l},{C5,l},
    				{Eb5,s},{E5,lll},
    				{A5,s},{B5,s},{C6,l},
    				{A5,s},{B5,s},{C6,l},
    				{D6,ll},
    				{E6,s},{F6,s},{G6,l},
    				{A6,l},{B6,l},{C7,lll}
    		};
    	
    		//adds each note backward to the temp using add note
    		for (int x = notes.length-1; x >= 0; x--) {
    			// if it is the 5th or sixth note
    			if (x == 5 || x == 6) { 
    				double frequency = notes[x][0];
    	    		int noteLengthInMilliseconds = (int)notes[x][1];
    	    		temp = addNote(temp, frequency, noteLengthInMilliseconds);
    	    		
    	    		//adds a silence of 500 milliseconds after the 5th and 6th notes
    				double silence = 0;
    	    		int silenceLengthInMilliseconds = 500;
    	    		temp = addNote(temp, silence, silenceLengthInMilliseconds);
    			}
    			else {
    				//adds a note
    				double frequency = notes[x][0];
    	    		int noteLengthInMilliseconds = (int)notes[x][1];
    	    		temp = addNote(temp, frequency , noteLengthInMilliseconds);
    	    		//adds a silence of 100 milliseconds after the note
    				double silence = 0;
    	    		int silenceLengthInMilliseconds = 100;
    	    		temp = addNote(temp, silence, silenceLengthInMilliseconds);
    		
    				
    			}
    		}

  		
    		

//    		?? etc etc down to
//    				double C7	= 2093.00;
 
/* ----- template code commented out BEGIN 

****Extra info - the entire tune is
*					C4l G4l C5l
				     E5s Eb5lll 
				     C4l G4l C5l
				     Eb5s E5lll 
				     A5s B5s C6l
				     A5s B5s C6l
				     D6ll
				     E6s F6s G6l 
				     E6s F6s G6l 
				     A6l B6l C7lll
*
		// create an empty AudioInputStream (of frame size 0)	
		// call it temp (already declared above) 
		byte[] c = new byte[1];
                AudioInputStream temp = new AudioInputStream(new ByteArrayInputStream(c),ais.getFormat(),0);

		// specify variable names for both the frequencies in Hz and note lengths in seconds 
		// eg double C4, D4 etc for frequencies and s, l, ll, lll for lengths 
		// Hint: Each octave results in a doubling of frequency.

		double C4	= 261.63;
		double D4	= 293.66; 	
		double Eb4  = 311.13;
		double E4	= 329.63; 	
		double F4	= 349.23; 	
		double G4	= 392.00; 	
		double A4	= 440.00; 	
		double B4	= 493.88; 
		double C5	= 523.25; 	
		double D5	= 587.33; 
		double Eb5  = 622.25;
		
		?? etc etc down to
		double C7	= 2093.00; 
		//There should be silences of length 100 milliseconds between all - except 5th and 6th
		// and the lengths in milliseconds
        	int s = 500;
		?? etc 

		// also sprach zarathustra: 2001 A Space Odyssey 
		// specify the tune
		double [][] notes = { 
				     {C4,l}, etc etc ??
				    };
		
		// use addNote to build the tune as an AudioInputStream
		// starting from the empty AudioInputStream temp (above) and adding each note one by one using A LOOP 
		?? 

 ----- template code commented out END */

		// append temp, ie the tune, to current ais 
       		return append(temp,ais);
    }

/**** altChannels *****************************************************************/

    public static AudioInputStream altChannels(AudioInputStream ais, double timeInterval){

	int frameSize 	  = ais.getFormat().getFrameSize(); // = 4
    float frameRate   = ais.getFormat().getFrameRate();
     // number of frames played during timeInterval
	int frameInterval = frameSize/frameSize;
	int inputLengthInBytes = (int) (ais.getFrameLength()*frameSize);
	int numChannels        = ais.getFormat().getChannels(); // = 2


    	
/* ----- template code commented out BEGIN 

	int frameSize 	  = ais.getFormat().getFrameSize(); // = 4
        float frameRate   = ?? 
	int frameInterval = ?? // number of frames played during timeInterval
	int inputLengthInBytes = ??
	int numChannels        = ais.getFormat().getChannels(); // = 2

 ----- template code commented out END */

	// byte arrays for input channels and output channels
	byte[] ich0, ich1, och0, och1;
	byte[] a=null, b=null;

	try {

		
		
		
/* ----- template code commented out BEGIN 

		     // create new byte arrays a for input and b for output of the right size
		     // ??
		     // fill the byte array a with the data of the AudioInputStream
		     // ??
		     // create new byte arrays for input and output channels of the right size 	
	    	     // eg ich0 = new byte[a.length/numChannels];
		     // ??

		     // fill up ich0 and ich1 by splitting a
		     ?? 

		     // ----------------------------------------------------------------------------
		     // compute the output channels from the input channels - this is the hard part:
		   
		     // int N = ?? ; // explained in CW3 worksheet - bytes per segment Ai (or Bi)
		     // int L = ?? ; //  length of (either) input array 
		     // int outL = ?? ; // length of (either) output array 
          
		     // index i marks out start positions of double segments Ai O, (or Bi O) each of length 2*N
		     // index j counts individual bytes in segments Ai, each of length N, going from 0 to N-1
		     // index k counts individual bytes in the final segment E (or F), of length R = outL % N, going from 0 to R-1
	  
		     // MAIN CODE HERE MAKING USE OF i, j, k, N, R 
		     ?? ..... 

		     // END OF compute the output channels from the input channels 
		     // ----------------------------------------------------------------------------

		     // finally ... join och0 and och1 into b
	              for (int i=0; i < b.length; i += 4) {
		      	b[i]   = och0[i/2];
			// etc etc 
		       }

 ----- template code commented out END */

	    // fill up b using och0 and och1 
	              for (int i=0; i < b.length; i += 4) {
//		      	  b[i]   = och0[i/2];
			  // ??
		      }

	} catch(Exception e){
	    System.out.println("Something went wrong");
	    e.printStackTrace();
	}
	
	// return b 
	return new AudioInputStream(new ByteArrayInputStream(b),
				    ais.getFormat(), b.length/ais.getFormat().getFrameSize());

    } // end altChannels

} // AudioManipulation

