package org.dromara.mica.mqtt.core.server.test;

import org.dromara.mica.mqtt.core.server.session.TrieTopicManager;
import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.info.GraphLayout;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TrieTopicManager 深度内存分析工具
 * 使用反射分析内部结构的内存占用
 *
 * @author AI Assistant
 */
public class TrieTopicManagerDeepAnalysis {

    /**
     * 深度分析 TrieTopicManager 的内存占用
     */
    public static void deepAnalyze(TrieTopicManager topicManager) {
        System.out.println("=== TrieTopicManager 深度内存分析 ===");
        
        try {
            // 分析 root 节点
            analyzeRootNode(topicManager);
            
            // 分析 share 分组
            analyzeShareGroups(topicManager);
            
            // 分析 queue 分组
            analyzeQueueGroup(topicManager);
            
            // 分析整体内存分布
            analyzeOverallMemory(topicManager);
            
        } catch (Exception e) {
            System.err.println("深度分析失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 分析 root 节点
     */
    private static void analyzeRootNode(TrieTopicManager topicManager) throws Exception {
        System.out.println("\n--- Root 节点分析 ---");
        
        Field rootField = TrieTopicManager.class.getDeclaredField("root");
        rootField.setAccessible(true);
        Object rootNode = rootField.get(topicManager);
        
        if (rootNode != null) {
            System.out.println("Root 节点类型: " + rootNode.getClass().getName());
            System.out.println("Root 节点内存占用: " + 
                              GraphLayout.parseInstance(rootNode).totalSize() + " bytes");
            
            // 分析 root 节点的子节点
            analyzeNodeChildren(rootNode, "root");
        }
    }

    /**
     * 分析 share 分组
     */
    private static void analyzeShareGroups(TrieTopicManager topicManager) throws Exception {
        System.out.println("\n--- Share 分组分析 ---");
        
        Field shareField = TrieTopicManager.class.getDeclaredField("share");
        shareField.setAccessible(true);
        Object shareMap = shareField.get(topicManager);
        
        if (shareMap instanceof ConcurrentHashMap) {
            ConcurrentHashMap<?, ?> shareGroups = (ConcurrentHashMap<?, ?>) shareMap;
            System.out.println("Share 分组数量: " + shareGroups.size());
            
            long totalShareSize = 0;
            int totalChildren = 0;
            
            for (Map.Entry<?, ?> entry : shareGroups.entrySet()) {
                String groupName = entry.getKey().toString();
                Object groupNode = entry.getValue();
                
                long groupSize = GraphLayout.parseInstance(groupNode).totalSize();
                totalShareSize += groupSize;
                
                System.out.println("  分组 '" + groupName + "': " + groupSize + " bytes");
                
                // 分析每个分组的子节点
                int childrenCount = analyzeNodeChildren(groupNode, "share." + groupName);
                totalChildren += childrenCount;
            }
            
            System.out.println("Share 分组总内存占用: " + totalShareSize + " bytes");
            System.out.println("Share 分组总子节点数: " + totalChildren);
        }
    }

    /**
     * 分析 queue 分组
     */
    private static void analyzeQueueGroup(TrieTopicManager topicManager) throws Exception {
        System.out.println("\n--- Queue 分组分析 ---");
        
        Field queueField = TrieTopicManager.class.getDeclaredField("queue");
        queueField.setAccessible(true);
        Object queueNode = queueField.get(topicManager);
        
        if (queueNode != null) {
            System.out.println("Queue 节点类型: " + queueNode.getClass().getName());
            System.out.println("Queue 节点内存占用: " + 
                              GraphLayout.parseInstance(queueNode).totalSize() + " bytes");
            
            // 分析 queue 节点的子节点
            analyzeNodeChildren(queueNode, "queue");
        }
    }

    /**
     * 分析节点的子节点
     */
    private static int analyzeNodeChildren(Object node, String nodePath) throws Exception {
        Field childrenField = node.getClass().getDeclaredField("children");
        childrenField.setAccessible(true);
        Object childrenMap = childrenField.get(node);
        
        if (childrenMap instanceof Map) {
            Map<?, ?> children = (Map<?, ?>) childrenMap;
            int childrenCount = children.size();
            
            if (childrenCount > 0) {
                System.out.println("  " + nodePath + " 子节点数: " + childrenCount);
                
                // 只显示前几个子节点的详细信息，避免输出过多
                int displayCount = Math.min(childrenCount, 5);
                int index = 0;
                
                for (Map.Entry<?, ?> entry : children.entrySet()) {
                    if (index >= displayCount) {
                        if (childrenCount > displayCount) {
                            System.out.println("  ... 还有 " + (childrenCount - displayCount) + " 个子节点");
                        }
                        break;
                    }
                    
                    String childKey = entry.getKey().toString();
                    Object childNode = entry.getValue();
                    
                    long childSize = GraphLayout.parseInstance(childNode).totalSize();
                    System.out.println("    " + nodePath + "." + childKey + ": " + childSize + " bytes");
                    
                    index++;
                }
            }
            
            return childrenCount;
        }
        
        return 0;
    }

    /**
     * 分析整体内存分布
     */
    private static void analyzeOverallMemory(TrieTopicManager topicManager) {
        System.out.println("\n--- 整体内存分析 ---");
        
        GraphLayout layout = GraphLayout.parseInstance(topicManager);
        
        System.out.println("总内存占用: " + layout.totalSize() + " bytes");
        System.out.println("对象数量: " + layout.totalCount());
        
        System.out.println("\n内存分布详情:");
        System.out.println(layout.toFootprint());
    }

    /**
     * 分析内存增长模式
     */
    public static void analyzeMemoryGrowthPattern() {
        System.out.println("=== 内存增长模式分析 ===");
        
        TrieTopicManager topicManager = new TrieTopicManager();
        long baseSize = GraphLayout.parseInstance(topicManager).totalSize();
        
        System.out.println("初始内存占用: " + baseSize + " bytes");
        System.out.println("订阅数量\t内存增长\t平均每订阅内存\t内存效率");
        
        for (int batchSize = 100; batchSize <= 5000; batchSize += 100) {
            // 添加一批订阅
            for (int i = 0; i < batchSize; i++) {
                topicManager.addSubscribe("/test/" + i + "/topic", "client" + i, i % 3);
            }
            
            long currentSize = GraphLayout.parseInstance(topicManager).totalSize();
            long memoryIncrease = currentSize - baseSize;
            double avgMemoryPerSubscription = (double) memoryIncrease / batchSize;
            double memoryEfficiency = (double) batchSize / memoryIncrease * 1000; // 每KB内存支持的订阅数
            
            System.out.printf("%d\t\t%d bytes\t%.2f bytes\t%.2f 订阅/KB%n", 
                            batchSize, memoryIncrease, avgMemoryPerSubscription, memoryEfficiency);
        }
    }

    /**
     * 分析不同 topic 模式的内存占用
     */
    public static void analyzeTopicPatternMemory() {
        System.out.println("=== 不同 Topic 模式内存分析 ===");
        
        String[] patterns = {
            "/simple/topic",                    // 简单路径
            "/sys/+/+/thing/model/down_raw",    // 通配符路径
            "$share/group1/sys/123/456/topic", // 共享订阅
            "$queue/sys/123/456/topic",         // 队列订阅
            "/very/long/topic/path/with/many/levels/and/segments" // 长路径
        };
        
        for (String pattern : patterns) {
            TrieTopicManager topicManager = new TrieTopicManager();
            
            // 添加多个客户端订阅相同模式
            for (int i = 0; i < 100; i++) {
                topicManager.addSubscribe(pattern, "client" + i, i % 3);
            }
            
            long memorySize = GraphLayout.parseInstance(topicManager).totalSize();
            System.out.printf("模式: %-50s | 内存: %s | 平均每订阅: %.2f bytes%n", 
                            pattern, 
                            formatBytes(memorySize), 
                            (double) memorySize / 100);
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
     * 主方法，运行所有分析
     */
    public static void main(String[] args) {
        // 创建测试数据
        TrieTopicManager topicManager = createTestData();
        
        // 运行深度分析
        deepAnalyze(topicManager);
        
        System.out.println("\n" + "================================================================================\n");
        
        // 分析内存增长模式
        analyzeMemoryGrowthPattern();
        
        System.out.println("\n" + "================================================================================\n");
        
        // 分析不同 topic 模式
        analyzeTopicPatternMemory();
    }

    /**
     * 创建测试数据
     */
    private static TrieTopicManager createTestData() {
        TrieTopicManager topicManager = new TrieTopicManager();
        
        // 添加各种类型的订阅
        topicManager.addSubscribe("/sys/1/456/thing/model/down_raw", "client1", 1);
        topicManager.addSubscribe("/sys/+/+/thing/model/down_raw", "client1", 0);
        topicManager.addSubscribe("$share/group1/sys/123/456/thing/model/down_raw", "client1", 0);
        topicManager.addSubscribe("$queue/sys/123/456/thing/model/down_raw", "client1", 1);
        
        topicManager.addSubscribe("/sys/2/456/thing/model/down_raw", "client2", 1);
        topicManager.addSubscribe("/sys/+/+/thing/model/down_raw", "client2", 0);
        topicManager.addSubscribe("$share/group1/sys/123/456/thing/model/down_raw", "client2", 1);
        
        return topicManager;
    }
}
