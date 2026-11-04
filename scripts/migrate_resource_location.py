#!/usr/bin/env python3
"""
SBM-port 批量改名脚本 —— 第 1 步：ResourceLocation -> Identifier
==============================================================
处理规则（顺序敏感，从上到下）：
1. 先处理类名 ResourceLocationKeyframe -> IdentifierKeyframe（避免被通用替换误伤）
2. import net.minecraft.resources.ResourceLocation -> import net.minecraft.resources.Identifier
3. new ResourceLocation(ns, path)  (双参) -> Identifier.fromNamespaceAndPath(ns, path)
4. new ResourceLocation(path)      (单参) -> Identifier.parse(path)
5. ResourceLocation.Serializer     -> 替换为自定义 IdentifierSerializer（需人工，此处先标记）
6. ResourceLocation.class          -> Identifier.class
7. 剩余裸 ResourceLocation         -> Identifier

用法：python scripts/migrate_resource_location.py
"""
import re
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # SBM-port 根目录
SRC = os.path.join(ROOT, "src", "main", "java")

# 统计
changed_files = []

def collect_java_files(base):
    for dirpath, _, files in os.walk(base):
        for f in files:
            if f.endswith(".java"):
                yield os.path.join(dirpath, f)

def process_file(path):
    with open(path, "r", encoding="utf-8") as fh:
        content = fh.read()
    original = content

    # 1. 类名 ResourceLocationKeyframe -> IdentifierKeyframe（先做，避免被后续裸替换误伤）
    content = content.replace("ResourceLocationKeyframe", "IdentifierKeyframe")

    # 2. import 语句
    content = content.replace(
        "import net.minecraft.resources.ResourceLocation;",
        "import net.minecraft.resources.Identifier;"
    )

    # 3. 双参构造 new ResourceLocation(ns, path) -> Identifier.fromNamespaceAndPath(ns, path)
    #    匹配 new ResourceLocation( 后面跟 "xxx, yyy" 的形式
    #    先处理双参（逗号分隔），再处理单参
    def repl_double(m):
        args = m.group(1)
        return "Identifier.fromNamespaceAndPath(" + args + ")"
    content = re.sub(r"new ResourceLocation\(([^()]*,[^()]*)\)", repl_double, content)

    # 4. 单参构造 new ResourceLocation(x) -> Identifier.parse(x)
    content = re.sub(r"new ResourceLocation\(([^(),]*)\)", lambda m: "Identifier.parse(" + m.group(1) + ")", content)

    # 5. ResourceLocation.Serializer -> 26.2 无内置 Serializer。
    #    用占位符保护，裸替换后人工统一改成自定义 IdentifierSerializer。
    content = content.replace("ResourceLocation.Serializer", "__RL_SERIALIZER_PLACEHOLDER__")

    # 6. ResourceLocation.class -> Identifier.class
    content = content.replace("ResourceLocation.class", "Identifier.class")

    # 7. 剩余裸 ResourceLocation -> Identifier
    content = content.replace("ResourceLocation", "Identifier")

    # 8. 还原占位符为待人工处理的标记（保留原语义，便于后续定位）
    content = content.replace("__RL_SERIALIZER_PLACEHOLDER__", "Identifier.Serializer")

    if content != original:
        with open(path, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(content)
        changed_files.append(os.path.relpath(path, ROOT))

def main():
    for path in collect_java_files(SRC):
        process_file(path)

    print(f"处理完成：{len(changed_files)} 个文件被修改")
    for f in changed_files:
        print("  -", f)

    # 检查是否还有残留的 ResourceLocation（作为类型）
    residual = []
    for path in collect_java_files(SRC):
        with open(path, "r", encoding="utf-8") as fh:
            c = fh.read()
        if "ResourceLocation" in c:
            residual.append((os.path.relpath(path, ROOT), c.count("ResourceLocation")))
    if residual:
        print("\n⚠️  仍有 ResourceLocation 残留（需人工确认）：")
        for f, n in residual:
            print(f"  - {f} ({n} 处)")
    else:
        print("\n✓ 无 ResourceLocation 残留")

if __name__ == "__main__":
    main()
