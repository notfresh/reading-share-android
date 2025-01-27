import os
import re
from collections import Counter

def extract_words(text):
    """提取文本中的英语单词"""
    # 匹配英语单词的正则表达式，包括驼峰命名法拆分
    pattern = r'[A-Z]?[a-z]+|[A-Z]{2,}(?=[A-Z][a-z]|\d|\W|$)|\d+'
    words = re.findall(pattern, text)
    # 转换为小写并过滤掉数字
    return [word.lower() for word in words if not word.isdigit()]

def count_code_lines(file_path):
    """统计单个文件的代码行数，排除空行和注释行，并统计单词"""
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
        lines = content.splitlines()
        
    total_lines = len(lines)
    empty_lines = len([l for l in lines if l.strip() == ''])
    
    # 计算注释行（包括单行和多行注释）
    comment_lines = 0
    in_block_comment = False
    for line in lines:
        line = line.strip()
        
        # 跳过空行
        if not line:
            continue
            
        # 处理块注释
        if '/*' in line and '*/' in line:
            comment_lines += 1
        elif '/*' in line:
            in_block_comment = True
            comment_lines += 1
        elif '*/' in line:
            in_block_comment = False
            comment_lines += 1
        elif in_block_comment:
            comment_lines += 1
        # 处理单行注释
        elif line.startswith('//'):
            comment_lines += 1
    
    # 统计单词
    words = extract_words(content)
    word_count = Counter(words)
            
    code_lines = total_lines - empty_lines - comment_lines
    return {
        'total': total_lines,
        'code': code_lines,
        'comment': comment_lines,
        'empty': empty_lines,
        'words': word_count
    }

def scan_directory(directory):
    """扫描目录下的所有Java文件"""
    stats = {
        'files': 0,
        'total_lines': 0,
        'code_lines': 0,
        'comment_lines': 0,
        'empty_lines': 0,
        'word_stats': Counter(),
        'file_details': []
    }
    
    for root, _, files in os.walk(directory):
        for file in files:
            if file.endswith('.java'):
                file_path = os.path.join(root, file)
                relative_path = os.path.relpath(file_path, directory)
                
                try:
                    file_stats = count_code_lines(file_path)
                    stats['files'] += 1
                    stats['total_lines'] += file_stats['total']
                    stats['code_lines'] += file_stats['code']
                    stats['comment_lines'] += file_stats['comment']
                    stats['empty_lines'] += file_stats['empty']
                    stats['word_stats'].update(file_stats['words'])
                    
                    stats['file_details'].append({
                        'path': relative_path,
                        'stats': file_stats
                    })
                except Exception as e:
                    print(f"Error processing {file_path}: {str(e)}")
    
    return stats

def generate_report(stats):
    """生成统计报告"""
    report = []
    report.append("代码统计报告")
    report.append("=" * 50)
    
    # 总体统计
    report.append("\n总体统计:")
    report.append("-" * 50)
    report.append(f"文件总数: {stats['files']}")
    report.append(f"总行数: {stats['total_lines']}")
    report.append(f"代码行数: {stats['code_lines']}")
    report.append(f"注释行数: {stats['comment_lines']}")
    report.append(f"空行数: {stats['empty_lines']}")
    report.append(f"总单词数: {sum(stats['word_stats'].values())}")
    report.append(f"不同单词数: {len(stats['word_stats'])}")
    
    # 文件列表
    report.append("\n文件列表:")
    report.append("-" * 50)
    sorted_files = sorted(stats['file_details'], 
                         key=lambda x: x['stats']['code'], 
                         reverse=True)
    for file_detail in sorted_files:
        report.append(f"{file_detail['path']} ({file_detail['stats']['code']} 行代码)")
    
    # 全局单词统计
    report.append("\n全局单词统计:")
    report.append("-" * 50)
    report.append("最常用的单词 (出现次数 > 10):")
    for word, count in stats['word_stats'].most_common():
        if count > 10:
            report.append(f"  {word}: {count}")
    
    return "\n".join(report)

def main():
    # 指定要统计的Java代码目录
    java_dir = "app/src/main/java"
    
    if not os.path.exists(java_dir):
        print(f"目录不存在: {java_dir}")
        return
    
    print(f"正在统计目录: {java_dir}")
    stats = scan_directory(java_dir)
    report = generate_report(stats)
    
    # 保存报告到文件
    with open('code_stats_report.txt', 'w', encoding='utf-8') as f:
        f.write(report)
    
    print("\n报告已生成到 code_stats_report.txt")
    print(report)

if __name__ == "__main__":
    main() 