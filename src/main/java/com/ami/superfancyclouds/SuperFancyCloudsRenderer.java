package com.ami.superfancyclouds;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.ShaderProgram;
import net.minecraft.client.util.ColorUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.util.random.RandomGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SuperFancyCloudsRenderer {

	private final Identifier whiteTexture = new Identifier("superfancyclouds", "white.png");

	public SimplexNoiseSampler cloudNoise = new SimplexNoiseSampler(RandomGenerator.createLegacy());

	public VertexBuffer cloudBuffer;

	public boolean[][][] _cloudData = new boolean[96][32][96];

	public Thread dataProcessThread;
	public boolean isProcessingData = false;

	public int moveTimer = 40;
	public double partialOffset = 0;
	public double partialOffsetSecondary = 0;

	public double time;

	public int fullOffset = 0;

	public double xScroll;
	public double zScroll;

	public BufferBuilder.RenderedBuffer cb;

	public void init() {
		cloudNoise = new SimplexNoiseSampler(RandomGenerator.createLegacy());
		isProcessingData = false;
	}

	public void tick() {

		if (MinecraftClient.getInstance().player == null)
			return;

		//If already processing, don't start up again.
		if (isProcessingData)
			return;

		var player = MinecraftClient.getInstance().player;

		var xScroll = MathHelper.floor(player.getX() / 16) * 16;
		var zScroll = MathHelper.floor(player.getZ() / 16) * 16;

		int timeOffset = (int) (Math.floor(time / 6) * 6);

		if (timeOffset != moveTimer || xScroll != this.xScroll || zScroll != this.zScroll) {
			moveTimer = timeOffset;
			isProcessingData = true;

			dataProcessThread = new Thread(() -> collectCloudData(xScroll, zScroll));
			dataProcessThread.start();
		}
	}

	public void render(ClientWorld world, MatrixStack matrices, Matrix4f projectionMatrix, float tickDelta, double cameraX, double cameraY, double cameraZ) {
		float f = world.getSkyProperties().getCloudsHeight();

		if (!Float.isNaN(f)) {
			//Setup render system
			RenderSystem.disableCull();
			RenderSystem.enableBlend();
			RenderSystem.enableDepthTest();
			RenderSystem.blendFuncSeparate(
					GlStateManager.class_4535.SRC_ALPHA,
					GlStateManager.class_4534.ONE_MINUS_SRC_ALPHA,
					GlStateManager.class_4535.ONE,
					GlStateManager.class_4534.ONE_MINUS_SRC_ALPHA
			);
			RenderSystem.depthMask(true);

			Vec3d cloudColor = world.getCloudsColor(tickDelta);

			synchronized (this) {
				//Fix up partial offset...
				partialOffset += MinecraftClient.getInstance().getLastFrameDuration() * 0.25f * 0.25f;
				partialOffsetSecondary += MinecraftClient.getInstance().getLastFrameDuration() * 0.25f * 0.25f;

				time += MinecraftClient.getInstance().getLastFrameDuration() / 20.0f;

				var cb = cloudBuffer;

				if (cb == null && this.cb != null && !isProcessingData) {
					cloudBuffer = new VertexBuffer();
					cloudBuffer.bind();
					cloudBuffer.upload(this.cb);
					cb = cloudBuffer;
					VertexBuffer.unbind();
				}


				if (cb != null) {
					//Setup shader
					RenderSystem.setShader(GameRenderer::getPositionTexColorNormalShader);
					RenderSystem.setShaderTexture(0, whiteTexture);
					BackgroundRenderer.setShaderFogColor();
					RenderSystem.setShaderFogStart(RenderSystem.getShaderFogStart() * 2);
					RenderSystem.setShaderFogEnd(RenderSystem.getShaderFogEnd() * 4);

					RenderSystem.setShaderColor((float) cloudColor.x, (float) cloudColor.y, (float) cloudColor.z, 1);

					matrices.push();
					matrices.translate(-cameraX, -cameraY, -cameraZ);
					matrices.translate(xScroll, f, zScroll + partialOffset);
					cb.bind();

					for (int s = 0; s < 2; ++s) {
						if (s == 0) {
							RenderSystem.colorMask(false, false, false, false);
						} else {
							RenderSystem.colorMask(true, true, true, true);
						}

						ShaderProgram shaderProgram = RenderSystem.getShader();
						cb.setShader(matrices.peek().getPosition(), projectionMatrix, shaderProgram);
					}

					VertexBuffer.unbind();
					matrices.pop();

					//Restore render system
					RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
					RenderSystem.enableCull();
					RenderSystem.disableBlend();
				}
			}
		}
	}

	public void clean() {

		try {
			if (dataProcessThread != null)
				dataProcessThread.join();
		} catch (Exception e) {
			//Ignore...
		}
	}

	public BufferBuilder builder = new BufferBuilder(2097152);
	public FloatArrayList vertexList = new FloatArrayList();
	public ByteArrayList normalList = new ByteArrayList();

	private final float[][] normals = {
			{1, 0, 0},
			{-1, 0, 0},
			{0, 1, 0},
			{0, -1, 0},
			{0, 0, 1},
			{0, 0, -1},
	};

	private final int[] colors = {
			ColorUtil.ARGB32.getArgb((int) (255 * 0.8f), 255, 255, 255),
			ColorUtil.ARGB32.getArgb((int) (255 * 0.8f), (int) (255 * 0.6f), (int) (255 * 0.6f), (int) (255 * 0.6f)),
			ColorUtil.ARGB32.getArgb((int) (255 * 0.8f), (int) (255 * 0.8f), (int) (255 * 0.8f), (int) (255 * 0.8f)),
			ColorUtil.ARGB32.getArgb((int) (255 * 0.8f), (int) (255 * 0.75f), (int) (255 * 0.75f), (int) (255 * 0.75f)),
			ColorUtil.ARGB32.getArgb((int) (255 * 0.8f), (int) (255 * 0.8f), (int) (255 * 0.8f), (int) (255 * 0.8f)),
			ColorUtil.ARGB32.getArgb((int) (255 * 0.8f), (int) (255 * 0.75f), (int) (255 * 0.75f), (int) (255 * 0.75f)),
	};

	double remappedValue(double noise) {
		return (Math.pow(Math.sin(Math.toRadians(((noise * 180) + 302) * 1.15)), 0.28) + noise - 0.5f) * 2;
	}

	private void collectCloudData(double scrollX, double scrollZ) {


		try {
			double startX = scrollX / 16;
			double startZ = scrollZ / 16;

			double timeOffset = Math.floor(time / 6) * 6;

			synchronized (this) {
				while (partialOffsetSecondary >= 16) {
					partialOffsetSecondary -= 16;
					fullOffset++;
				}
			}

			Random r = new Random();

			float baseFreq = 0.05f;
			float baseTimeFactor = 0.01f;

			float l1Freq = 0.09f;
			float l1TimeFactor = 0.02f;

			float l2Freq = 0.001f;
			float l2TimeFactor = 0.1f;

			for (int cx = 0; cx < 96; cx++) {
				for (int cy = 0; cy < 32; cy++) {
					for (int cz = 0; cz < 96; cz++) {
						double cloudVal = cloudNoise.sample(
								(startX + cx + (timeOffset * baseTimeFactor)) * baseFreq,
								(cy - (timeOffset * baseTimeFactor * 2)) * baseFreq,
								(startZ + cz - fullOffset) * baseFreq
						);
						double cloudVal1 = cloudNoise.sample(
								(startX + cx + (timeOffset * l1TimeFactor)) * l1Freq,
								(cy - (timeOffset * l1TimeFactor)) * l1Freq,
								(startZ + cz - fullOffset) * l1Freq
						);
						double cloudVal2 = cloudNoise.sample(
								(startX + cx + (timeOffset * l2TimeFactor)) * l2Freq,
								0,
								(startZ + cz - fullOffset) * l2Freq
						);

						//Smooth floor function...
						cloudVal2 *= 3;
						cloudVal2 = (cloudVal2 - (Math.sin(Math.PI * 2 * cloudVal2) / (Math.PI * 2))) / 2.0f;

						cloudVal = ((cloudVal + (cloudVal1 * 0.8f)) / 1.8f) * cloudVal2;

						cloudVal = cloudVal * remappedValue(1 - ((double) (cy + 1) / 32));

						_cloudData[cx][cy][cz] = cloudVal > (0.5f);
					}
				}
			}

			var tmp = rebuildCloudMesh();

			synchronized (this) {
				cb = tmp;
				cloudBuffer = null;

				this.xScroll = scrollX;
				this.zScroll = scrollZ;

				while (partialOffset >= 16) {
					partialOffset -= 16;
				}
			}
		} catch (Exception e) {
			// -- Ignore...
		}

		isProcessingData = false;
	}

	public void addVertex(float x, float y, float z) {
		vertexList.add(x - 48);
		vertexList.add(y);
		vertexList.add(z - 48);
	}

	private BufferBuilder.RenderedBuffer rebuildCloudMesh() {

		vertexList.clear();
		normalList.clear();

		for (int cx = 0; cx < 96; cx++) {
			for (int cy = 0; cy < 32; cy++) {
				for (int cz = 0; cz < 96; cz++) {
					if (!_cloudData[cx][cy][cz])
						continue;

					//Right
					if (cx == 95 || !_cloudData[cx + 1][cy][cz]) {
						addVertex(cx + 1, cy, cz);
						addVertex(cx + 1, cy, cz + 1);
						addVertex(cx + 1, cy + 1, cz + 1);
						addVertex(cx + 1, cy + 1, cz);

						normalList.add((byte) 0);
					}

					//Left....
					if (cx == 0 || !_cloudData[cx - 1][cy][cz]) {
						addVertex(cx, cy, cz);
						addVertex(cx, cy, cz + 1);
						addVertex(cx, cy + 1, cz + 1);
						addVertex(cx, cy + 1, cz);

						normalList.add((byte) 1);
					}

					//Up....
					if (cy == 31 || !_cloudData[cx][cy + 1][cz]) {
						addVertex(cx, cy + 1, cz);
						addVertex(cx + 1, cy + 1, cz);
						addVertex(cx + 1, cy + 1, cz + 1);
						addVertex(cx, cy + 1, cz + 1);

						normalList.add((byte) 2);
					}

					//Down
					if (cy == 0 || !_cloudData[cx][cy - 1][cz]) {
						addVertex(cx, cy, cz);
						addVertex(cx + 1, cy, cz);
						addVertex(cx + 1, cy, cz + 1);
						addVertex(cx, cy, cz + 1);

						normalList.add((byte) 3);
					}


					//Forward....
					if (cz == 95 || !_cloudData[cx][cy][cz + 1]) {
						addVertex(cx, cy, cz + 1);
						addVertex(cx + 1, cy, cz + 1);
						addVertex(cx + 1, cy + 1, cz + 1);
						addVertex(cx, cy + 1, cz + 1);

						normalList.add((byte) 4);
					}

					//Backward
					if (cz == 0 || !_cloudData[cx][cy][cz - 1]) {
						addVertex(cx, cy, cz);
						addVertex(cx + 1, cy, cz);
						addVertex(cx + 1, cy + 1, cz);
						addVertex(cx, cy + 1, cz);

						normalList.add((byte) 5);
					}
				}
			}
		}

		builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR_NORMAL);

		try {
			int vertCount = vertexList.size() / 3;

			for (int i = 0; i < vertCount; i++) {
				int origin = i * 3;
				var x = vertexList.getFloat(origin) * 16;
				var y = vertexList.getFloat(origin + 1) * 8;
				var z = vertexList.getFloat(origin + 2) * 16;

				int normIndex = normalList.getByte(i / 4);
				var norm = normals[normIndex];
				var nx = norm[0];
				var ny = norm[1];
				var nz = norm[2];

				builder.vertex(x, y, z).uv(0.5f, 0.5f).color(colors[normIndex]).normal(nx, ny, nz).next();
			}
		} catch (Exception e) {
			// -- Ignore...
			SuperFancyCloudsMod.LOGGER.error(e.toString());
		}

		return builder.end();
	}
}
