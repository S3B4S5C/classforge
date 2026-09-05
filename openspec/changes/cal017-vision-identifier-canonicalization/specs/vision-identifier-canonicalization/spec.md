# Vision identifier canonicalization

## Requirements

### R1
Raw visual class/attribute evidence SHALL remain unchanged.

### R2
New visual class names SHALL be deterministically converted to CODE_NAME before CREATE_CLASS.

### R3
New visual attribute names SHALL be deterministically converted to CODE_NAME.

### R4
CUSTOM type names SHALL be deterministically converted to CODE_NAME.

### R5
Already-valid CODE_NAME identifiers SHALL remain byte-for-byte unchanged after trim.

### R6
Canonicalization SHALL remove Latin diacritics.

### R7
Unsupported character runs SHALL become one underscore.

### R8
Leading digits SHALL be prefixed with underscore.

### R9
Canonicalization that cannot yield a meaningful ASCII identifier SHALL fail closed.

### R10
Canonical collisions SHALL fail closed; no suffixes SHALL be invented.

### R11
Existing class/attribute matching SHALL remain normalization tolerant.

### R12
ORIGINAL benchmark PLAN attempts SHALL pass resolve -> preview -> ProjectDocumentValidator.

### R13
A non-executable ORIGINAL plan SHALL NOT be Semantic Exact.

### R14
ORIGINAL executable rate SHALL be 100% for benchmark success.
