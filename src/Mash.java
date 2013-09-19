
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.Random;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.apache.commons.math.complex.Complex;
import org.apache.commons.math.transform.FastFourierTransformer;
import org.jblas.ComplexDoubleMatrix;


public class Mash {


	//Difference of 4 = good
	//Difference of 2 = static
	//Difference of 0 = no echo
	//Difference > 4 = crap
	//8192, 16384, 32768, 65536, 131072
	static int chunkSize = 65536;
	static int target = 16384;
	static int offset = (chunkSize - target) / 2;
	static Song song1;
	static Song song2;

	  
	public static void main(String args[]) throws UnsupportedAudioFileException, IOException {
			  
	      String fileDirectory = "C:\\Users\\Mallific\\Music\\mash_music\\mash\\mash_parts\\mash";
		  song1 = new Song(fileDirectory, "9-beatfx.wav");
		  song2 = new Song(fileDirectory, "9-mod.wav");
	      
	      song1.mash(song2, fileDirectory);
	  }
	
	  
	  public static class Song{
		  String name;
		  AudioInputStream ais;
		  ByteArrayOutputStream out;
		  int length;
		  
		  private Double[] fftreverse;
		  private short[] sOutArr;
		  
		  private Complex[][] fftvals;

		  public Song(ComplexDoubleMatrix u, ComplexDoubleMatrix s, ComplexDoubleMatrix v, int minLength, String name){
			  this.length = minLength;
			  this.name = name;
		  }
		  
		  public Song(String fileDirectory, String fileName) throws UnsupportedAudioFileException, IOException{
			  this.name = fileName;
			  
			  ais = AudioSystem.getAudioInputStream(new File(fileDirectory + File.separator + fileName));
			  out = new ByteArrayOutputStream();
			  
			  byte[] buffer = new byte[4096];
		      int counter;
		      while ((counter = ais.read(buffer, 0, buffer.length)) != -1) {
		          if (counter > 0) {
		              out.write(buffer, 0, counter);
		          }
		      }
		      ais.close();
		      out.close();
		      
		      calculateShortBytes(getBytes());
		  }
		  
		    public void mash(Song other, String outputDirectory) throws IOException{
			  int minLength = minLength(other);
		      
			  this.calculateSVD(minLength);
			  other.calculateSVD(minLength);
			  
			  minLength = other.getFFTVals().length > this.fftvals.length ? this.fftvals.length : other.getFFTVals().length;
			  
			  Complex[][] fft1 = fftvals;
			  Complex[][] fft2 = other.getFFTVals();
			  			
			  length = (fft1.length + fft2.length) * target;
			  Complex[][] finalfft = new Complex[fft1.length + fft2.length][fft1[0].length];
			  
			  for(int i = 0; i < finalfft.length; i++){
				  if(i < fft1.length){
					  finalfft[i] = fft1[i];
				  } else {
					  finalfft[i] = fft2[i - fft1.length];
				  }
			  }
			  
			  Random rand = new Random();
			  int index = 0;
			  Complex temp;
			  Complex[] temp2 = new Complex[fft2[0].length];
			  
			  index = 0;
			  for(int i = finalfft.length - 1; i > 0; i--){
				  index = rand.nextInt(i);
				  temp2 = finalfft[index];
				  finalfft[index] = finalfft[i];
				  finalfft[i] = temp2;
			  }
			  
			  for(int i = finalfft.length - 1; i > 0; i--){
				  index = rand.nextInt(i);
				  for(int k = offset; k < target; k++){
					  temp = finalfft[index][k];
					  finalfft[index][k] = finalfft[i][k];
					  finalfft[i][k] = temp;			      
				  }
			  }  
			  
			  String mashName = name + "-" + other.getName();
			  outputSong(mashName + "_mash.wav", finalfft);
		  }
		  
		  private void calculateSVD(int shortestLength){
			  short[] sOutCopy = new short[shortestLength];
			  for(int i = 0; i < shortestLength; i++){
				  sOutCopy[i] = this.sOutArr[i];
			  }
			  this.sOutArr = sOutCopy;
			  fftvals = fftTransform(sOutArr);
		  }
		  
		  private  byte[] getBytes(){
			  return out.toByteArray();
		  }
		  
		  private AudioInputStream getAIS(){
			  return ais;
		  }
		  
		  private int minLength(Song other){
			  return this.length < other.length ? this.length : other.length;
		  }
		  
		  public void outputSong(String fileDirectory, Complex[][] c2arr) throws IOException{
			  fftreverse = reverseFFTTransform(c2arr);
			  
			  byte[] outputSong = new byte[fftreverse.length * 2];
			  ByteBuffer byteBuffer = ByteBuffer.wrap(outputSong);
			  for (Double d : fftreverse){
				  if(d == null){
					  byteBuffer.putShort((short) 0);
				  } else {
					  byteBuffer.putShort(d.shortValue());
				  }
			  }
			  
		      
		      AudioInputStream bytewav = new AudioInputStream(new ByteArrayInputStream(outputSong), song1.getAIS().getFormat(), song1.getAIS().getFrameLength() + song2.getAIS().getFrameLength());
		
		      AudioSystem.write(bytewav, AudioFileFormat.Type.WAVE, new File(fileDirectory /* File.separator + name*/));
		  }
		  
		  private Double[] reverseFFTTransform(Complex[][] trans){
		      FastFourierTransformer fftTrans = new FastFourierTransformer();
			  Double[] fftreverse = new Double[length];
			  int index = 0;
			  Complex[] reverseWork = new Complex[chunkSize];
			  for (int k=0; k < trans.length; k++) {
				  reverseWork = fftTrans.inversetransform(trans[k]);
				  for(int i = 0; i < target; i++){
					  fftreverse[index + i] = reverseWork[offset + i].getReal();
				  }
				  index += target;
			  }
			  return fftreverse;
		  }
		  
		  private static Complex[][] fftTransform(short[] shortArray){
		      FastFourierTransformer fftTrans = new FastFourierTransformer();

			  double[] toTrans = new double[chunkSize];
			  Complex[][] trans = new Complex[shortArray.length / target][chunkSize];
			  int index = 0;
			
			  for (int k=0; k < shortArray.length / target; k++) {
				  for (int i = 0; i < chunkSize; i++) {
					  //Left buffer is before audio
					  if(index + i - offset < 0){
						  toTrans[i] = 0;
					  //Buffer completely in audio
				  	  } else if(index + i - offset < shortArray.length) {
						  toTrans[i] = shortArray[index + i - offset];	
					  //Right buffer past end of audio
					  } else {
						  toTrans[i] = 0;
					  }
				  }		
				  
				  trans[k] = fftTrans.transform(toTrans);
				  index += target;
			  }
			  
			  return trans;
		  }
		  
		  private void calculateShortBytes(byte[] bytes){
			  sOutArr = new short[bytes.length/2];
			  ByteBuffer bb = ByteBuffer.wrap(bytes); // Wrapper around underlying byte[].
			  ShortBuffer sb = bb.asShortBuffer(); // Wrapper around ByteBuffer.
			  for (int i=0; sb.remaining() > 0; i++) {
				  sOutArr[i] = sb.get();
			  }
		      length = sOutArr.length;
		  }
		  
		  public String getName(){
			  return this.name;
		  }
		  
		  public Complex[][] getFFTVals(){
			  return this.fftvals;
		  }
	  }
}