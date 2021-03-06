/**
 * Copyright (C) <2012> <Lazaro Brito>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software 
 * and associated documentation files (the "Software"), to deal in the Software without restriction, 
 * including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, 
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, 
 * subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all copies or substantial 
 * portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, 
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR 
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE 
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, 
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package alterrs.deob;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipFile;

import EDU.purdue.cs.bloat.editor.MethodEditor;
import alterrs.deob.trans.ControlFlowDeobfuscation;
import alterrs.deob.trans.FieldDeobfuscation;
import alterrs.deob.trans.HandlerDeobfuscation;
import alterrs.deob.trans.PrivilageDeobfuscation;
import alterrs.deob.trans.TryCatchDeobfuscation;
import alterrs.deob.util.NodeVisitor;

public class Deobfuscator {
	private static Application app = null;

	public static final NodeVisitor[] MISC_TRANSFORMERS = new NodeVisitor[] {
			new HandlerDeobfuscation(), new PrivilageDeobfuscation(),
	// new MonitorDeobfuscation()
	};

	public static final NodeVisitor[] TREE_TRANSFORMERS = new NodeVisitor[] {
			new ControlFlowDeobfuscation(), new TryCatchDeobfuscation(),
			new FieldDeobfuscation(), };

	static {
		MethodEditor.OPT_STACK_2 = true;
	}

	private static Object lock = new Object();

	public static void main(String[] args) {
		try {
			System.out.println("Loading application... [" + args[0] + "]");
			app = new Application(new ZipFile(args[0]));
			System.out.println("Loaded " + app.size() + " classes!");
			System.out.println();

			System.out.print("Applying misc transformers...");
			for (NodeVisitor visitor : MISC_TRANSFORMERS) {
				app.accept(visitor);
			}
			System.out.println(" DONE!");
			for (NodeVisitor visitor : MISC_TRANSFORMERS) {
				visitor.onFinish();
			}
			System.out.println();

			Chunk[] chunks = app.split(32 * Runtime.getRuntime()
					.availableProcessors());
			totalChunks = chunks.length;
			System.out.println("Application split into " + chunks.length
					+ " chunks!");

			System.out.print("Applying tree transformers... 0%");
			ExecutorService executor = Executors.newFixedThreadPool(Runtime
					.getRuntime().availableProcessors());
			for (int i = 0; i < chunks.length; i++) {
				executor.submit(chunks[i]);
			}
			synchronized (lock) {
				lock.wait();
			}
			System.out.println();

			for (NodeVisitor visitor : TREE_TRANSFORMERS) {
				visitor.onFinish();
			}
			System.out.println();

			String output = args[1].replace("$t",
					new StringBuilder().append(System.currentTimeMillis()));
			System.out.println("Saving application... [" + output + "]");
			app.save(new File(output));
			System.out.println("DONE!");

			executor.shutdownNow();
		} catch (Throwable t) {
			System.err.println("Failed to run deobfuscator!");
			t.printStackTrace();
		}
	}

	private static AtomicInteger percent = new AtomicInteger(0);
	private static double finishedChunks = 0;
	private static double totalChunks = 0;

	public static void onFinish(Chunk chunk) {
		int p = percent.get();
		finishedChunks++;

		int p_ = (int) ((finishedChunks / totalChunks) * 100);
		if (p != p_) {
			percent.set(p_);
			System.out.print(" " + p_ + "%");

			if (p_ == 100) {
				synchronized (lock) {
					lock.notifyAll();
				}
			}
		}
	}

	public static Application getApp() {
		return app;
	}
}
