# onnxruntime 体积优化调研(2026-10-29)

> **结论先行:这次没动 onnx。** 本次任务是接入 agent,onnx 属于既有功能,
> 改动它会扩大回归面。以下是实测数据,供以后需要时决策 —— 不是待办。

## 现状(reading-share 2.4.0 / arm64-v8a)

| 组成 | 大小 | 说明 |
|---|---|---|
| `assets/models/bge-micro-v2.onnx` | 16.6 MB | BGE-Micro v2 embedding 模型 |
| `lib/arm64-v8a/libonnxruntime.so` | 14.0 MB | ONNX Runtime 运行时(全算子版) |
| `lib/arm64-v8a/libonnxruntime4j_jni.so` | 0.7 MB | JNI 桥 |
| **合计** | **31.3 MB** | 占 APK 54.9MB 的 57% |

依赖声明:`com.microsoft.onnxruntime:onnxruntime-android:1.16.3`
使用方:`service/EmbeddingService.java` → `embedding/TagEmbeddingManager.java`
(标签合并靠嵌入余弦相似度)

## 一、模型文件(16.6MB)—— **压不动**

模型**已经是 QAT(量化感知训练)版**:

```
算子分布:  MatMulInteger × 24, DynamicQuantizeLinear × 24, DequantizeLinear × 3
权重精度:  FLOAT × 53, INT8 × 36, UINT8 × 6
```

动态量化实测(本机 onnxruntime 1.29.0 + onnx 1.23.2):

| 方式 | 体积 | 逐样本余弦相似度 |
|---|---|---|
| 原模型 | 16.60 MB | — |
| 动态量化 INT8 | 16.61 MB(**省 -0.1%**) | 1.00000 |
| 动态量化 QUInt8 | 16.61 MB(**省 -0.1%**) | 1.00000 |
| fp16 | 工具缺失(可装 `onnxconverter-common`) | — |

**结论:量化省不了体积**。权重已是 8 位,没有 fp32 可压。
onnxruntime 自己也警告:*"Please check if the model is already quantized"*。

## 二、运行时库(14MB)—— **能压到 3.3MB,但有代价**

`onnxruntime-mobile` 官方包(同样是 1.16.3):

| 包 | aar 大小 | arm64 .so |
|---|---|---|
| `onnxruntime-android`(当前用的) | 21.8 MB | **14.0 MB** |
| `onnxruntime-mobile` | 6.5 MB | **3.3 MB** |

**省 10.7MB**。新版(`onnxruntime-android` 1.17~1.20)反而更大,别升级。

### 代价:必须用 ORT format 模型

mobile 版是 `--minimal_build=extended` 编的,只注册有限算子,要求 ORT format 模型。

**算子兼容性已逐个核对**(mobile 版 .so 里的符号):

```
Gelu ✅  MatMulIntegerToFloat ✅  DynamicQuantizeMatMul ✅  LayerNormalization ✅
MatMulInteger ✅  DynamicQuantizeLinear ✅  DequantizeLinear ✅  Softmax ✅
```

`Erf` 两版都没有 —— 但 ORT format 转换会把 Erf 融进 `com.microsoft::Gelu`,
所以不需要 Erf。

转换 + 推理实测:

```bash
python3 -c "
from onnxruntime.tools.convert_onnx_models_to_ort import convert_onnx_models_to_ort
convert_onnx_models_to_ort(Path('bge-micro-v2.onnx'), Path('out/ort'))
"
# 产出 bge-micro-v2.ort (16.61 MB) + bge-micro-v2.required_operators.config
```

ORT format 模型本机推理通过(输出 `[2,16,384]`,与原模型一致)。

### 落地需要改的代码

`service/EmbeddingService.java:39` 的 `MODEL_PATH` 从
`models/bge-micro-v2.onnx` 改成 `models/bge-micro-v2.ort`,并把模型文件换掉。
模型本身大小不变(16.6MB),省的是运行时那 10.7MB。

**必须重测嵌入质量** —— 标签合并依赖余弦相似度,精度不能崩。

## 三、为什么没做

1. 本次任务是接入 agent,onnx 是既有功能 —— 混在一起出问题不好定位
2. 改 `EmbeddingService` 的模型加载路径 = 动既有功能(AGENTS.md 第四条:先报告再改)
3. 当前 APK 已经 54.9MB,比 master 的 77MB 还小 —— 不急

## 四、顺带发现:mobile 版 + 4 ABI 是更优解

`onnxruntime-mobile` 的 aar 只有 6.5MB。换成它之后,
**4 个 ABI 全带也只有约 63MB**(vs 现在的全量版 137MB)。

也就是说:将来要发布给大众用户时,换 mobile 版比砍 ABI 更划算 ——
既保住全设备覆盖,又省一半体积。

| 方案 | APK | 设备覆盖 |
|---|---|---|
| 当前(全量 onnxruntime + arm64) | 54.9 MB | 仅 arm64 |
| mobile onnxruntime + 4 ABI | ~63 MB | 全覆盖 ✅ |
| 全量 onnxruntime + 4 ABI | 137 MB | 全覆盖 |

## 复现命令

```bash
# 看 APK 里各部分体积
python3 -c "
import zipfile, collections
z = zipfile.ZipFile('app/build/outputs/apk/debug/app-debug.apk')
cat = collections.Counter()
for i in z.infolist():
    n = i.filename
    k = ('lib/'+n.split('/')[1]) if n.startswith('lib/') else \
        ('assets/chaquopy' if n.startswith('assets/chaquopy/') else 'other')
    cat[k] += i.compress_size
for k,v in cat.most_common(): print(f'{k:22} {v/1024/1024:7.1f} MB')
"

# 量化实测
python3 -m pip install onnx onnxruntime
python3 -c "
from onnxruntime.quantization import quantize_dynamic, QuantType
quantize_dynamic('app/src/main/assets/models/bge-micro-v2.onnx', '/tmp/q.onnx', weight_type=QuantType.QInt8)
"
```
