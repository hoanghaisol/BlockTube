#!/usr/bin/env python3
"""Export the e5 (XLM-R) Unigram vocabulary to the compact binary read by
UnigramTokenizer.java, and optionally write a parity fixture.

Usage:
  export_tokenizer.py <tokenizer.json> <out vocab.bin> [<out fixture.json>]

vocab.bin layout (big endian, java.io.DataInputStream):
  int magic 0x45355643 ("E5VC"), int version 1, int unkId, int count,
  then per id: float score, short byteLen, UTF-8 bytes.

The fixture needs the `tokenizers` package and holds the reference ids that
UnigramTokenizerTest compares against.
"""
import json
import struct
import sys

MAGIC = 0x45355643

FIXTURE_TEXTS = [
    "query: Troll người yêu cực gắt",
    "query: Kênh Troll Vui. Troll người yêu cực gắt; Prank bạn thân bằng gián giả",
    "Cách nấu phở bò chuẩn vị Hà Nội",
    "Thử thách 24h ăn mì cay 🔥🔥 #shorts",
    "Pranking my girlfriend gone WRONG!!! (not clickbait)",
    "  multiple   spaces\tand\ttabs\nnew lines  ",
    "Ｆｕｌｌｗｉｄｔｈ ＡＢＣ １２３ and ①②③",
    "Tiếng Việt dạng tổ hợp: Tiếng Việt",  # decomposed diacritics
    "日本語のタイトル 【公式】 MV",
    "Русский заголовок видео 2024",
    "BẢN TIN THỜI SỰ 19H | VTV24",
    "Bé học đếm số từ 1 đến 10 - Nhạc thiếu nhi",
    "a",
    "",
]


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    with open(sys.argv[1], encoding="utf-8") as f:
        tok_json = json.load(f)

    model = tok_json["model"]
    assert model["type"] == "Unigram", model["type"]
    vocab = model["vocab"]

    with open(sys.argv[2], "wb") as out:
        out.write(struct.pack(">iiii", MAGIC, 1, model["unk_id"], len(vocab)))
        for piece, score in vocab:
            data = piece.encode("utf-8")
            out.write(struct.pack(">fh", score, len(data)))
            out.write(data)

    if len(sys.argv) > 3:
        from tokenizers import Tokenizer

        tokenizer = Tokenizer.from_file(sys.argv[1])
        cases = [{"text": t, "ids": tokenizer.encode(t).ids} for t in FIXTURE_TEXTS]
        with open(sys.argv[3], "w", encoding="utf-8") as f:
            json.dump(cases, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
