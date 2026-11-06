package com.ddk.compat;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * 本模块 {@code src/test/resources/compat/} 下有 fixture 的已发布版本。跨版本测试对每个版本各跑一遍，
 * 新版本的 fixture 目录放进来就会被读取；目录里缺了本模块的哪个文件，读取它的测试就失败。
 * <p>
 * 构建传入 {@code ddk.compat.baseline}（根 pom 的 {@code api.baseline.version}）时，那个版本必须在列表里：
 * 发布后改了 API 基线却没有生成 fixture，测试会失败。
 */
public final class Releases {

    private static final Comparator<String> BY_VERSION = Comparator.comparing(Releases::parts, Arrays::compare);

    private Releases() {
    }

    public static List<String> versions() {
        TreeSet<String> versions = new TreeSet<>(BY_VERSION);
        try {
            for (Resource file : new PathMatchingResourcePatternResolver().getResources("classpath*:compat/*/*")) {
                String path = file.getURL().getPath();
                String[] segments = path.split("/");
                versions.add(segments[segments.length - 2]);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (versions.isEmpty()) {
            throw new IllegalStateException("No compatibility fixtures under compat/ in the test resources");
        }
        String baseline = System.getProperty("ddk.compat.baseline");
        if (baseline != null && !baseline.isBlank() && !versions.contains(baseline)) {
            throw new IllegalStateException("The API baseline " + baseline + " has no compatibility fixtures under compat/" + baseline
                    + "/; generate them with compat/generator (see compat/README.md)");
        }
        return List.copyOf(versions);
    }

    public static String read(String version, String file) {
        try {
            return new ClassPathResource("compat/" + version + "/" + file).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing fixture compat/" + version + "/" + file, e);
        }
    }

    private static int[] parts(String version) {
        return Arrays.stream(version.split("[.-]")).map(part -> part.replaceAll("\\D", "")).filter(part -> !part.isEmpty())
                .mapToInt(Integer::parseInt).toArray();
    }
}
