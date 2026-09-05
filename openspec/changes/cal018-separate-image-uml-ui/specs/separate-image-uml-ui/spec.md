# Separate image to UML UI

## Requirements

### R1
The conversational assistant SHALL be separate from the image-to-UML flow.

### R2
The image flow SHALL be a sibling workspace component and retain upload,
drag/drop, preparation, retry, and canonical store Apply.

### R3
Normal assistant and image UI SHALL NOT expose technical runtime diagnostics.

### R4
The image preview SHALL remain bounded and its edit controls SHALL be icon-only
with accessible labels.

### R5
READY SHALL present only a concise confirmation and Apply/Discard actions.

### R6
Image-plan warnings and backend diagnostic payloads SHALL NOT be rendered in
the normal UI.

### R7
The change SHALL NOT alter backend, API, revision guards, Command Bus, or
canonical Apply behavior.
