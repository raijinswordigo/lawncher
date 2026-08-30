#ifndef LAWNCHER_TYPES_H
#define LAWNCHER_TYPES_H

typedef struct Vector3 {
	float x;
	float y;
	float z;
} Vector3;

typedef struct Vector2 {
	float x;
	float y;
} Vector2;

typedef struct Rectangle {
	float x;
	float y;
	float width;
	float height;
} Rectangle;

typedef struct FloatColor {
	float r;
	float g;
	float b;
	float a;
} FloatColor;

typedef struct Quaternion {
	float x;
	float y;
	float z;
	float w;
} Quaternion;

typedef struct Matrix4 {
	float m[16];
} Matrix4;

#endif //LAWNCHER_TYPES_H
