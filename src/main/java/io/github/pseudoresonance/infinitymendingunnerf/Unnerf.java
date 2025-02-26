package io.github.pseudoresonance.infinitymendingunnerf;

import java.io.File;
import java.lang.reflect.Field;
import java.util.logging.Logger;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.matcher.ElementMatchers;

public class Unnerf extends JavaPlugin {

	private final Logger logger = this.getLogger();

	@Override
	public void onEnable() {
		if (!tryImplementationOverride()) {
			String bukkitPackageName = Bukkit.getServer().getClass().getPackage().getName();
			String bukkitVersion = null;
			int ver = 0;
			try {
				bukkitVersion = bukkitPackageName.substring(bukkitPackageName.lastIndexOf(".") + 1);
				ver = Integer.parseInt(bukkitVersion.split("_")[1]);
				logger.info("Loading in Minecraft 1." + ver);
			} catch (IndexOutOfBoundsException | NumberFormatException e) {
				logger.severe("Could not get Minecraft version from " + bukkitPackageName + "! Attempting load anyways as Minecraft 1.17 or later!");
				ver = 17;
			}
			if (ver >= 11) {
				logger.info("Minecraft 1.11 or later.");
				if ("9".compareTo(System.getProperty("java.version")) >= 0) {
					logger.info("Running Java 8 or older!");
					JavaCompiler c = ToolProvider.getSystemJavaCompiler();
					if (c == null) {
						logger.info("Running JRE!");
						this.getDataFolder().mkdirs();
						File tools = new File(this.getDataFolder(), "tools.jar");
						if (!tools.isFile())
							logger.info("If using JRE, please place tools.jar at: " + tools.getAbsolutePath());
						if (System.getProperty("net.bytebuddy.agent.toolsjar") == null)
							System.setProperty("net.bytebuddy.agent.toolsjar", tools.getAbsolutePath());
						File attachDll = new File(this.getDataFolder(), "attach.dll");
						File attachSo = new File(this.getDataFolder(), "attach.so");
						if (!attachDll.isFile() && !attachSo.isFile())
							logger.info("If using JRE, please place attach.dll or attach.so in directory: " + this.getDataFolder().getAbsolutePath());
						if (System.getProperty("java.library.path") == null)
							System.setProperty("java.library.path", this.getDataFolder().getAbsolutePath());
						else
							System.setProperty("java.library.path", this.getDataFolder().getAbsolutePath() + File.pathSeparator + System.getProperty("java.library.path"));
						if (System.getProperty("jna.library.path") == null)
							System.setProperty("jna.library.path", this.getDataFolder().getAbsolutePath());
						else
							System.setProperty("jna.library.path", this.getDataFolder().getAbsolutePath() + File.pathSeparator + System.getProperty("jna.library.path"));
						try {
							Field fieldSysPath = ClassLoader.class.getDeclaredField("sys_paths");
							fieldSysPath.setAccessible(true);
							fieldSysPath.set(null, null);
							logger.info("Current Java library path: " + System.getProperty("java.library.path"));
							logger.info("Current JNA library path: " + System.getProperty("jna.library.path"));
							System.loadLibrary("attach");
							logger.info("Initialized " + System.mapLibraryName("attach") + "!");
						} catch (Exception | Error e) {
							logger.severe("Failed to initialize attach.dll/attach.so!");
							e.printStackTrace();
						}
					} else {
						logger.info("Running JDK! Proceeding...");
					}
				}
				logger.info("Loading injector!");
				ByteBuddyAgent.install();
				try {
					logger.info("Injector loaded! Beginning injection of custom mending/infinity code!");
					Class<?> enchantmentInfiniteArrowsClass;
					Class<?> enchantmentClass;
					if (ver >= 17) {
						enchantmentInfiniteArrowsClass = Class.forName("net.minecraft.world.item.enchantment.EnchantmentInfiniteArrows");
						enchantmentClass = Class.forName("net.minecraft.world.item.enchantment.Enchantment");
					} else {
						enchantmentInfiniteArrowsClass = Class.forName("net.minecraft.server." + bukkitVersion + ".EnchantmentInfiniteArrows");
						enchantmentClass = Class.forName("net.minecraft.server." + bukkitVersion + ".Enchantment");
					}
					logger.info("Found necessary classes!");
					new ByteBuddy().redefine(enchantmentInfiniteArrowsClass).method(ElementMatchers.takesArguments(enchantmentClass)).intercept(SuperMethodCall.INSTANCE).make().load(enchantmentInfiniteArrowsClass.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
					logger.info("Injection complete! Infinity and mending are no longer exclusive!");
				} catch (ClassNotFoundException e) {
					logger.severe("There was an error injecting code to allow infinity and mending!");
					e.printStackTrace();
				}
			} else {
				logger.severe("Infinity and mending were not exclusive before Minecraft 1.11! Disabling plugin!");
				Bukkit.getPluginManager().disablePlugin(this);
			}
		}
		initializeMetrics();
	}
	
	private void initializeMetrics() {
		Metrics metrics = new Metrics(this, 8021);
		metrics.addCustomChart(new SimplePie("java_type", () -> ToolProvider.getSystemJavaCompiler() == null ? "JRE" : "JDK"));
	}
	
	private boolean tryImplementationOverride() {
		try {
			Class<?> purpurConfig = Class.forName("net.pl3x.purpur.PurpurConfig");
			logger.info("Server is running Purpur, attempting Purpur override.");
			Field allowInfinityMending = purpurConfig.getField("allowInfinityMending");
			allowInfinityMending.setAccessible(true);
			allowInfinityMending.set(null, true);
			logger.info("Overrided purpur config! Infinity and mending are no longer exclusive!");
			return true;
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			logger.info("Unable to override, falling back to injection.");
		} catch (ClassNotFoundException e) {
			logger.info("Unable to find Purpur config class, falling back to injection.");
		}
		return false;
	}

}
