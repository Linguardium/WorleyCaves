package fluke.worleycaves.util;

import fluke.worleycaves.config.Configs;

public class ThreadedNoiseSegment implements Runnable
{

	private static int maxCaveHeight;
	private static float noiseCutoff;
	private static float warpAmplifier;
	private static float yCompression;
	private static float xzCompression;
	private static float minCaveHeight;

	private int chunkX;
	private int chunkZ;
	private int maxSurfaceHeight;
	private int yLevel;
	private int numLayers;
	private WorleyUtil worleyF1divF3;

	static
	{
		updateConfigVals();
	}

	public ThreadedNoiseSegment(int chunkX, int chunkZ, int yLevel, int numLayers, int maxHeight, WorleyUtil worleyF1divF3)
	{
		this.chunkX = chunkX;
		this.chunkZ = chunkZ;
		this.yLevel = yLevel;
		this.numLayers = numLayers;
		this.maxSurfaceHeight = maxHeight;
		this.worleyF1divF3 = worleyF1divF3;
	}
	
	@Override
	public void run()
	{
		float[][][] segmentSamples = new float[ThreadedNoiseManager.X_SAMPLE_SIZE][numLayers][ThreadedNoiseManager.Z_SAMPLE_SIZE];
		int originalMaxHeight = 128;
		float noise;
		for (int x = 0; x < ThreadedNoiseManager.X_SAMPLE_SIZE; x++)
		{
			int realX = x*4 + chunkX*16;
			for (int z = 0; z < ThreadedNoiseManager.Z_SAMPLE_SIZE; z++)
			{
				int realZ = z*4 + chunkZ*16;

				for(int y = 0; y < numLayers; y++)
				{
					int currentLayer = yLevel + y*ThreadedNoiseManager.NUM_THREADS;
					if(currentLayer >= ThreadedNoiseManager.Y_SAMPLE_SIZE)
						continue;
					
					float realY = (currentLayer)*2;
					if(realY > maxSurfaceHeight || realY > maxCaveHeight || realY < minCaveHeight)
					{
						//if outside of valid cave range set noise value below normal minimum of -1.0
						segmentSamples[x][y][z] = -1.1F;
					}
					else
					{
						//Experiment making the cave system more chaotic the more you descend 
						///TODO might be too dramatic down at lava level
						float dispAmp = (float) (warpAmplifier * ((originalMaxHeight - (currentLayer)) / (originalMaxHeight * 0.85)));
						
						float xDisp = 0f;
						float yDisp = 0f;
						float zDisp = 0f;
						
						xDisp = ThreadedNoiseManager.displacementNoisePerlin.GetNoise(realX, realY, realZ)*dispAmp;
						yDisp = ThreadedNoiseManager.displacementNoisePerlin.GetNoise(realX, realY-256.0f, realZ)*dispAmp;
						zDisp = ThreadedNoiseManager.displacementNoisePerlin.GetNoise(realX, realY-512.0f, realZ)*dispAmp;

						//doubling the y frequency to get some more caves
						noise = worleyF1divF3.SingleCellular3Edge(realX*xzCompression+xDisp, realY*yCompression+yDisp, realZ*xzCompression+zDisp);
						segmentSamples[x][y][z] = noise;
						
						
						if (noise > noiseCutoff)
						{
							//if noise is below cutoff, adjust values of neighbors
							//helps prevent caves fracturing during interpolation
							
							if(x > 0)
								segmentSamples[x-1][y][z] = (noise*0.2f) + (segmentSamples[x-1][y][z]*0.8f);
							if(z > 0)
								segmentSamples[x][y][z-1] = (noise*0.2f) + (segmentSamples[x][y][z-1]*0.8f);

						}
					}
				}
			}
		}

		ThreadedNoiseManager.mergeSegmentNoise(segmentSamples, yLevel, numLayers);
	}

	public static void updateConfigVals()
	{
		maxCaveHeight = Configs.cavegen.maxCaveHeight;
		minCaveHeight = Configs.cavegen.minCaveHeight;
		noiseCutoff = (float) Configs.cavegen.noiseCutoffValue;
		warpAmplifier = (float) Configs.cavegen.warpAmplifier;
		yCompression = (float) Configs.cavegen.verticalCompressionMultiplier;
		xzCompression = (float) Configs.cavegen.horizonalCompressionMultiplier;
	}

}