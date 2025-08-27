package org.dromara.mica.mqtt.core.server.test;

import org.dromara.mica.mqtt.core.server.session.TrieTopicManager;
import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.info.GraphLayout;
import org.openjdk.jol.vm.VM;

import java.util.concurrent.TimeUnit;

/**
 * TrieTopicManager 内存占用分析测试
 * 使用 jol-core 进行详细的内存分析
 *
 * @author AI Assistant
 */
public class TrieTopicManagerMemoryAnalysisTest {

    public static void main(String[] args) {
        // 1. 显示 JVM 信息
        System.out.println("=== JVM 信息 ===");
        System.out.println(VM.current().details());
        System.out.println();

        // 2. 分析空 TrieTopicManager 的内存占用
        System.out.println("=== 空 TrieTopicManager 内存分析 ===");
        analyzeEmptyTrieTopicManager();
        System.out.println();

        // 3. 分析添加订阅后的内存占用
        System.out.println("=== 添加订阅后内存分析 ===");
        analyzeTrieTopicManagerWithSubscriptions();
        System.out.println();

        // 4. 分析大量订阅的内存占用
        System.out.println("=== 大量订阅内存分析 ===");
        analyzeTrieTopicManagerWithManySubscriptions();
        System.out.println();

        // 5. 分析共享订阅的内存占用
        System.out.println("=== 共享订阅内存分析 ===");
        analyzeTrieTopicManagerWithShareSubscriptions();
        System.out.println();

        // 6. 内存增长趋势分析
        System.out.println("=== 内存增长趋势分析 ===");
        analyzeMemoryGrowthTrend();

        // 7. 性能与内存对比测试
        System.out.println();
        performanceVsMemoryTest();
    }

    /**
     * 分析空 TrieTopicManager 的内存占用
     */
    private static void analyzeEmptyTrieTopicManager() {
        TrieTopicManager topicManager = new TrieTopicManager();

        // 分析对象头信息
        System.out.println("空 TrieTopicManager 对象布局:");
        System.out.println(ClassLayout.parseInstance(topicManager).toPrintable());

        // 分析整个对象图
        System.out.println("空 TrieTopicManager 对象图:");
        System.out.println(GraphLayout.parseInstance(topicManager).toPrintable());

        // 获取总内存占用
        long totalSize = GraphLayout.parseInstance(topicManager).totalSize();
        System.out.println("空 TrieTopicManager 总内存占用: " + totalSize + " bytes (" +
                          formatBytes(totalSize) + ")");
    }

    /**
     * 分析添加订阅后的内存占用
     */
    private static void analyzeTrieTopicManagerWithSubscriptions() {
        TrieTopicManager topicManager = new TrieTopicManager();

        // 添加一些订阅
        topicManager.addSubscribe("/sys/1/456/thing/model/down_raw", "client1", 1);
        topicManager.addSubscribe("/sys/2/456/thing/model/down_raw", "client1", 1);
        topicManager.addSubscribe("/sys/+/+/thing/model/down_raw", "client1", 0);
        topicManager.addSubscribe("/sys/1/456/thing/model/down_raw", "client2", 1);

        // 分析对象图
        System.out.println("添加订阅后的对象图:");
        System.out.println(GraphLayout.parseInstance(topicManager).toPrintable());

        // 获取总内存占用
        long totalSize = GraphLayout.parseInstance(topicManager).totalSize();
        System.out.println("添加订阅后总内存占用: " + totalSize + " bytes (" +
                          formatBytes(totalSize) + ")");
    }

    /**
     * 分析大量订阅的内存占用
     */
    private static void analyzeTrieTopicManagerWithManySubscriptions() {
        TrieTopicManager topicManager = new TrieTopicManager();

        // 添加大量订阅
        for (int i = 0; i < 1000; i++) {
            for (int j = 0; j < 10; j++) {
                topicManager.addSubscribe("/sys/" + i + "/" + j + "/thing/model/down_raw",
                                       "client" + i, j % 3);
            }
        }

        // 获取总内存占用
        long totalSize = GraphLayout.parseInstance(topicManager).totalSize();
        System.out.println("大量订阅后总内存占用: " + totalSize + " bytes (" +
                          formatBytes(totalSize) + ")");

        // 分析内存分布
        System.out.println("内存分布详情:");
        System.out.println(GraphLayout.parseInstance(topicManager).toFootprint());
    }

    /**
     * 分析共享订阅的内存占用
     */
    private static void analyzeTrieTopicManagerWithShareSubscriptions() {
        TrieTopicManager topicManager = new TrieTopicManager();

        // 添加共享订阅
        topicManager.addSubscribe("$share/group1/sys/123/456/thing/model/down_raw", "client1", 0);
        topicManager.addSubscribe("$share/group1/sys/123/456/thing/model/down_raw", "client2", 1);
        topicManager.addSubscribe("$share/group2/sys/123/456/thing/model/down_raw", "client3", 0);
        topicManager.addSubscribe("$queue/sys/123/456/thing/model/down_raw", "client4", 1);

        // 获取总内存占用
        long totalSize = GraphLayout.parseInstance(topicManager).totalSize();
        System.out.println("共享订阅后总内存占用: " + totalSize + " bytes (" +
                          formatBytes(totalSize) + ")");

        // 分析共享订阅的内存占用
        System.out.println("共享订阅内存分布:");
        System.out.println(GraphLayout.parseInstance(topicManager).toFootprint());
    }

    /**
     * 分析内存增长趋势
     */
    private static void analyzeMemoryGrowthTrend() {
        System.out.println("内存增长趋势分析:");
        System.out.println("订阅数量\t内存占用(bytes)\t内存占用(KB)\t内存占用(MB)");

        TrieTopicManager topicManager = new TrieTopicManager();
        long baseSize = GraphLayout.parseInstance(topicManager).totalSize();

        for (int subscriptionCount = 0; subscriptionCount <= 10000; subscriptionCount += 1000) {
            if (subscriptionCount > 0) {
                // 添加订阅
                for (int i = 0; i < 1000; i++) {
                    topicManager.addSubscribe("/sys/" + subscriptionCount + "/" + i + "/thing/model/down_raw",
                                           "client" + subscriptionCount, i % 3);
                }
            }

            long currentSize = GraphLayout.parseInstance(topicManager).totalSize();
            long memoryIncrease = currentSize - baseSize;

            System.out.printf("%d\t\t%d\t\t%.2f\t\t%.2f%n",
                            subscriptionCount,
                            memoryIncrease,
                            memoryIncrease / 1024.0,
                            memoryIncrease / (1024.0 * 1024.0));
        }
    }

    /**
     * 格式化字节数
     */
    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }

    /**
     * 性能与内存对比测试
     */
    public static void performanceVsMemoryTest() {
        System.out.println("=== 性能与内存对比测试 ===");

        // 测试不同订阅数量下的性能和内存
        for (int subscriptionCount = 10000; subscriptionCount <= 100000; subscriptionCount += 10000) {
            TrieTopicManager topicManager = new TrieTopicManager();

            // 添加订阅
            long startTime = System.nanoTime();
            for (int i = 0; i < subscriptionCount; i++) {
                topicManager.addSubscribe("/sys/" + i + "/thing/model/down_raw", "client" + i, i % 3);
            }
            long addTime = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - startTime);

            // 测试查找性能
            startTime = System.nanoTime();
            for (int i = 0; i < 10000; i++) {
                topicManager.searchSubscribe("/sys/500/thing/model/down_raw");
            }
            long searchTime = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - startTime);

            // 获取内存占用
            long memorySize = GraphLayout.parseInstance(topicManager).totalSize();

            System.out.printf("订阅数: %d, 添加耗时: %d μs, 查找耗时: %d μs, 内存: %s%n",
                            subscriptionCount, addTime, searchTime, formatBytes(memorySize));
        }
    }
}
