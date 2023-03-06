# API

## board
글 정보 가져올시 사용
- index (글 번호, *자동 생성됨*)
- title (글 제목)
- content (글 내용)
- writer (글쓴이, *수정 불가*)
- date (날짜, *자동 생성됨*)
- readCount (조회수, *자동 생성됨*)

### GET

- /board 전체 글 목록 가져옴
- /board/{index} 특정 글 번호 정보 가져옴

### POST

- /board 글 추가 (title, content, writer 필요)

### PUT

- /board 글 수정 (index, 수정할 정보 필요)

### 글 삭제

- /board/{index} 글 삭제