# Angular frontend generation specification

## Requirements

### Generated workspace
When Spring API generation is enabled, the ZIP MUST include a buildable Angular workspace at `frontend/`.

### Specific entity code
For every Domain Manifest entity, the generator MUST create dedicated models, API service, list, detail and form component files.

### Dashboard
The generated application MUST include a dashboard that uses each entity count endpoint and provides navigation to its list.

### Relationships
Direct/to-one relations MUST be editable with single selection. Many-to-many relations MUST support multiple selection.

### IDs
Simple and composite identifiers MUST use the same canonical HTTP contract as CU-14/CU-15.

### Simple mode
`SIMPLE_CRUD` MUST NOT generate login/JWT/guard/interceptor files.

### Auth mode
`AUTH_INFORMATION_SYSTEM` MUST generate login, one-time bootstrap UI, Bearer JWT storage/interceptor, route guard and logout. 401 responses from protected calls MUST clear the token and navigate to login.

### Password
The configured password attribute MUST use a password input and MUST NOT be rendered as readable response data.

### Primary color
The export request MUST accept a `#RRGGBB` primary color. The generated frontend MUST materialize it in its theme/CSS.

### Determinism
Equal canonical input + generation options MUST produce byte-identical generated frontend files.

### Acceptance
Representative Simple and Auth generated frontends MUST pass Angular production build using the supported ClassForge Angular toolchain.
