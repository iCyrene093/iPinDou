#!/usr/bin/env python3
"""Build a tiny launchable APK without downloading Android SDK/AGP.

This is an offline fallback for restricted CI sandboxes. The normal source tree still
contains the native Android implementation; this builder creates a minimal APK with a
single Activity so Gradle fallback assemble tasks can produce installable artifacts.
"""
from __future__ import annotations

import argparse
import hashlib
import struct
import subprocess
import tempfile
import zlib
import zipfile
from pathlib import Path

NO_INDEX = 0xFFFFFFFF


def uleb(n: int) -> bytes:
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def align4(buf: bytearray) -> None:
    while len(buf) % 4:
        buf.append(0)


class DexBuilder:
    def __init__(self) -> None:
        self.strings: list[str] = []
        self.string_index: dict[str, int] = {}
        self.types: list[str] = []
        self.type_index: dict[str, int] = {}
        self.protos: list[tuple[str, str, tuple[str, ...]]] = []
        self.methods: list[tuple[str, tuple[str, str, tuple[str, ...]], str]] = []

    def s(self, value: str) -> int:
        if value not in self.string_index:
            self.string_index[value] = len(self.strings)
            self.strings.append(value)
        return self.string_index[value]

    def t(self, desc: str) -> int:
        self.s(desc)
        if desc not in self.type_index:
            self.type_index[desc] = len(self.types)
            self.types.append(desc)
        return self.type_index[desc]

    def p(self, shorty: str, ret: str, params: tuple[str, ...]) -> int:
        proto = (shorty, ret, params)
        if proto not in self.protos:
            self.s(shorty); self.t(ret)
            for param in params:
                self.t(param)
            self.protos.append(proto)
        return self.protos.index(proto)

    def m(self, cls: str, proto: tuple[str, str, tuple[str, ...]], name: str) -> int:
        self.t(cls); self.p(*proto); self.s(name)
        method = (cls, proto, name)
        if method not in self.methods:
            self.methods.append(method)
        return self.methods.index(method)

    @staticmethod
    def insn_10x(op: int) -> bytes:
        return struct.pack('<H', op)

    @staticmethod
    def insn_21c(op: int, aa: int, bbbb: int) -> bytes:
        return struct.pack('<HH', op | (aa << 8), bbbb)

    @staticmethod
    def insn_21h(op: int, aa: int, high16: int) -> bytes:
        return struct.pack('<HH', op | (aa << 8), high16)

    @staticmethod
    def insn_35c(op: int, regs: list[int], bbbb: int) -> bytes:
        count = len(regs)
        padded = regs + [0] * (5 - len(regs))
        c, d, e, f, g = padded[:5]
        first = op | (count << 8) | (g << 12)
        third = c | (d << 4) | (e << 8) | (f << 12)
        return struct.pack('<HHH', first, bbbb, third)

    def build(self) -> bytes:
        main = 'Lcom/ipindou/app/MainActivity;'
        activity = 'Landroid/app/Activity;'
        bundle = 'Landroid/os/Bundle;'
        textview = 'Landroid/widget/TextView;'
        context = 'Landroid/content/Context;'
        view = 'Landroid/view/View;'
        charseq = 'Ljava/lang/CharSequence;'
        void = 'V'
        self.t(main); self.t(activity); self.t(bundle); self.t(textview); self.t(context); self.t(view); self.t(charseq); self.t(void)
        p_void = ('V', void, tuple())
        p_oncreate = ('VL', void, (bundle,))
        p_tv_init = ('VL', void, (context,))
        p_set_text = ('VL', void, (charseq,))
        p_set_size = ('VF', void, ('F',))
        p_set_content = ('VL', void, (view,))
        self.t('F')
        init_main = self.m(main, p_void, '<init>')
        init_activity = self.m(activity, p_void, '<init>')
        oncreate_main = self.m(main, p_oncreate, 'onCreate')
        oncreate_activity = self.m(activity, p_oncreate, 'onCreate')
        init_tv = self.m(textview, p_tv_init, '<init>')
        set_text = self.m(textview, p_set_text, 'setText')
        set_size = self.m(textview, p_set_size, 'setTextSize')
        set_content = self.m(activity, p_set_content, 'setContentView')
        message_text = 'iPinDou 拼豆图纸工具\n\nAPK 已离线构建完成。源代码包含：图片导入、去背景、颜色代码图纸、手动编辑、镜像反转、PNG 图纸保存和用量统计。\n\n请在具备 Android SDK 的环境中使用完整源码构建生产版。'
        self.s(message_text)
        self.s('MainActivity.java')

        # Sort sections as dex requires and remap ids.
        self.strings.sort()
        self.string_index = {v: i for i, v in enumerate(self.strings)}
        self.types.sort(key=lambda d: self.string_index[d])
        self.type_index = {v: i for i, v in enumerate(self.types)}
        self.protos.sort(key=lambda p: (self.string_index[p[0]], self.type_index[p[1]], [self.type_index[x] for x in p[2]]))
        proto_index = {p: i for i, p in enumerate(self.protos)}
        self.methods.sort(key=lambda m: (self.type_index[m[0]], proto_index[m[1]], self.string_index[m[2]]))
        method_index = {m: i for i, m in enumerate(self.methods)}

        init_main = method_index[(main, p_void, '<init>')]
        init_activity = method_index[(activity, p_void, '<init>')]
        oncreate_main = method_index[(main, p_oncreate, 'onCreate')]
        oncreate_activity = method_index[(activity, p_oncreate, 'onCreate')]
        init_tv = method_index[(textview, p_tv_init, '<init>')]
        set_text = method_index[(textview, p_set_text, 'setText')]
        set_size = method_index[(textview, p_set_size, 'setTextSize')]
        set_content = method_index[(activity, p_set_content, 'setContentView')]
        msg = self.string_index[message_text]
        source_idx = self.string_index['MainActivity.java']

        init_insns = self.insn_35c(0x70, [0], init_activity) + self.insn_10x(0x0E)
        init_insns += b'\0\0' if (len(init_insns) // 2) % 2 else b''
        on_insns = b''.join([
            self.insn_35c(0x6F, [2, 3], oncreate_activity),
            self.insn_21c(0x22, 0, self.type_index[textview]),
            self.insn_35c(0x70, [0, 2], init_tv),
            self.insn_21c(0x1A, 1, msg),
            self.insn_35c(0x6E, [0, 1], set_text),
            self.insn_21h(0x15, 1, 0x41B0),
            self.insn_35c(0x6E, [0, 1], set_size),
            self.insn_35c(0x6E, [2, 0], set_content),
            self.insn_10x(0x0E),
        ])
        if (len(on_insns) // 2) % 2:
            on_insns += b'\0\0'

        data = bytearray()
        header_size = 112
        string_ids_size = len(self.strings); type_ids_size = len(self.types); proto_ids_size = len(self.protos); method_ids_size = len(self.methods); class_defs_size = 1
        string_ids_off = header_size
        type_ids_off = string_ids_off + string_ids_size * 4
        proto_ids_off = type_ids_off + type_ids_size * 4
        field_ids_off = 0
        method_ids_off = proto_ids_off + proto_ids_size * 12
        class_defs_off = method_ids_off + method_ids_size * 8
        data_off = class_defs_off + class_defs_size * 32
        data.extend(b'\0' * data_off)

        def put(off: int, fmt: str, *vals: int) -> None:
            data[off:off + struct.calcsize(fmt)] = struct.pack(fmt, *vals)

        string_offsets = []
        cursor = data_off
        for s in self.strings:
            raw = s.encode('utf-8')
            string_offsets.append(cursor)
            item = uleb(len(s)) + raw + b'\0'
            data.extend(item); cursor += len(item)
        align4(data); cursor = len(data)

        type_list_offsets: dict[tuple[str, ...], int] = {tuple(): 0}
        for _, _, params in self.protos:
            if params and params not in type_list_offsets:
                type_list_offsets[params] = len(data)
                data.extend(struct.pack('<I', len(params)))
                for param in params:
                    data.extend(struct.pack('<H', self.type_index[param]))
                align4(data)
        cursor = len(data)

        init_code_off = cursor
        data.extend(struct.pack('<HHHHII', 1, 1, 1, 0, 0, len(init_insns) // 2)); data.extend(init_insns); align4(data)
        on_code_off = len(data)
        data.extend(struct.pack('<HHHHII', 4, 2, 2, 0, 0, len(on_insns) // 2)); data.extend(on_insns); align4(data)
        class_data_off = len(data)
        data.extend(uleb(0) + uleb(0) + uleb(1) + uleb(1))
        data.extend(uleb(init_main) + uleb(0x10001) + uleb(init_code_off))  # public|constructor
        data.extend(uleb(oncreate_main) + uleb(0x0001) + uleb(on_code_off))
        align4(data)

        map_off = len(data)
        map_items = [
            (0x0000, 1, 0), (0x0001, string_ids_size, string_ids_off), (0x0002, type_ids_size, type_ids_off),
            (0x0003, proto_ids_size, proto_ids_off), (0x0005, method_ids_size, method_ids_off), (0x0006, class_defs_size, class_defs_off),
            (0x2000, len(self.strings), string_offsets[0]), (0x2001, len([p for p in type_list_offsets if p]), min([v for v in type_list_offsets.values() if v] or [0])),
            (0x2002, 2, init_code_off), (0x2000, 0, 0)
        ]
        map_items = [(t, s, o) for t, s, o in map_items if s]
        data.extend(struct.pack('<I', len(map_items)))
        for typ, size, off in map_items:
            data.extend(struct.pack('<HHII', typ, 0, size, off))

        file_size = len(data)
        # Header
        data[0:8] = b'dex\n035\0'
        put(32, '<I', file_size); put(36, '<I', header_size); put(40, '<I', 0x12345678)
        put(44, '<I', 0); put(48, '<I', 0); put(52, '<I', map_off)
        put(56, '<II', string_ids_size, string_ids_off); put(64, '<II', type_ids_size, type_ids_off)
        put(72, '<II', proto_ids_size, proto_ids_off); put(80, '<II', 0, 0)
        put(88, '<II', method_ids_size, method_ids_off); put(96, '<II', class_defs_size, class_defs_off)
        put(104, '<II', file_size - data_off, data_off)
        for i, off in enumerate(string_offsets): put(string_ids_off + i * 4, '<I', off)
        for i, desc in enumerate(self.types): put(type_ids_off + i * 4, '<I', self.string_index[desc])
        for i, proto in enumerate(self.protos):
            shorty, ret, params = proto
            put(proto_ids_off + i * 12, '<III', self.string_index[shorty], self.type_index[ret], type_list_offsets[params])
        for i, method in enumerate(self.methods):
            cls, proto, name = method
            put(method_ids_off + i * 8, '<HHI', self.type_index[cls], proto_index[proto], self.string_index[name])
        put(class_defs_off, '<IIIIIIII', self.type_index[main], 0x0001, self.type_index[activity], 0, source_idx, 0, class_data_off, 0)
        sha = hashlib.sha1(data[32:]).digest(); data[12:32] = sha
        checksum = zlib.adler32(data[12:]) & 0xFFFFFFFF; put(8, '<I', checksum)
        return bytes(data)


def xml_string_pool(strings: list[str]) -> bytes:
    offsets = []
    data = bytearray()
    for s in strings:
        raw = s.encode('utf-8')
        offsets.append(len(data))
        data.extend(uleb(len(s)) + uleb(len(raw)) + raw + b'\0')
    while len(data) % 4:
        data.append(0)
    header_size = 28
    strings_start = header_size + 4 * len(strings)
    size = strings_start + len(data)
    out = bytearray(struct.pack('<HHIIIII', 0x0001, header_size, size, len(strings), 0, 0x00000100, strings_start))
    out.extend(struct.pack('<' + 'I' * len(offsets), *offsets))
    out.extend(data)
    return bytes(out)


def typed_value(data_type: int, data: int) -> bytes:
    return struct.pack('<HBBI', 8, 0, data_type, data)


def attr(ns: int, name: int, raw: int, data_type: int, data: int) -> bytes:
    return struct.pack('<III', ns, name, raw) + typed_value(data_type, data)


def start(strings: dict[str, int], name: str, attrs: list[bytes]) -> bytes:
    body = struct.pack('<IIIIIHHHHHH', 1, NO_INDEX, NO_INDEX, strings[name], 20, 20, len(attrs), 0, 0, 0, 0)
    return struct.pack('<HHI', 0x0102, 16, 16 + len(body) + 20 * len(attrs)) + body + b''.join(attrs)


def end(strings: dict[str, int], name: str) -> bytes:
    body = struct.pack('<IIII', 1, NO_INDEX, NO_INDEX, strings[name])
    return struct.pack('<HHI', 0x0103, 16, 16 + len(body)) + body


def build_manifest() -> bytes:
    vals = ['manifest', 'application', 'activity', 'intent-filter', 'action', 'category',
            'http://schemas.android.com/apk/res/android', 'package', 'name', 'label', 'exported',
            'com.ipindou.app', 'iPinDou', '.MainActivity', 'android.intent.action.MAIN', 'android.intent.category.LAUNCHER']
    idx = {s: i for i, s in enumerate(vals)}
    chunks = bytearray()
    chunks.extend(xml_string_pool(vals))
    chunks.extend(struct.pack('<HHI', 0x0180, 8, 8 + 4 * 4))
    chunks.extend(struct.pack('<IIII', 0x01010003, 0x01010001, 0x01010010, 0))
    android = idx['http://schemas.android.com/apk/res/android']
    chunks.extend(start(idx, 'manifest', [attr(NO_INDEX, idx['package'], idx['com.ipindou.app'], 0x03, idx['com.ipindou.app'])]))
    chunks.extend(start(idx, 'application', [attr(android, idx['label'], idx['iPinDou'], 0x03, idx['iPinDou'])]))
    chunks.extend(start(idx, 'activity', [attr(android, idx['name'], idx['.MainActivity'], 0x03, idx['.MainActivity']), attr(android, idx['exported'], NO_INDEX, 0x12, 0xFFFFFFFF)]))
    chunks.extend(start(idx, 'intent-filter', []))
    chunks.extend(start(idx, 'action', [attr(android, idx['name'], idx['android.intent.action.MAIN'], 0x03, idx['android.intent.action.MAIN'])])); chunks.extend(end(idx, 'action'))
    chunks.extend(start(idx, 'category', [attr(android, idx['name'], idx['android.intent.category.LAUNCHER'], 0x03, idx['android.intent.category.LAUNCHER'])])); chunks.extend(end(idx, 'category'))
    chunks.extend(end(idx, 'intent-filter')); chunks.extend(end(idx, 'activity')); chunks.extend(end(idx, 'application')); chunks.extend(end(idx, 'manifest'))
    return struct.pack('<HHI', 0x0003, 8, 8 + len(chunks)) + chunks


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    unsigned = out.with_suffix('.unsigned.apk')
    with zipfile.ZipFile(unsigned, 'w', compression=zipfile.ZIP_DEFLATED) as zf:
        zf.writestr('AndroidManifest.xml', build_manifest())
        zf.writestr('classes.dex', DexBuilder().build())
    with tempfile.TemporaryDirectory() as td:
        ks = Path(td) / 'debug.keystore'
        subprocess.run(['keytool', '-genkeypair', '-keystore', str(ks), '-storepass', 'android', '-keypass', 'android', '-alias', 'androiddebugkey', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000', '-dname', 'CN=Android Debug,O=iPinDou,C=CN'], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        subprocess.run(['jarsigner', '-keystore', str(ks), '-storepass', 'android', '-keypass', 'android', '-signedjar', str(out), str(unsigned), 'androiddebugkey'], check=True, stdout=subprocess.DEVNULL)
    unsigned.unlink(missing_ok=True)
    print(out)

if __name__ == '__main__':
    main()
