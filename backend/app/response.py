"""统一 API 响应结构。

客户端文档要求所有接口都返回同一种外壳，本文件负责生成该外壳。
"""

from time import time
from uuid import uuid4

from fastapi import HTTPException
from fastapi.responses import JSONResponse


def now_millis() -> int:
    """获取当前服务器毫秒时间。

    返回：
        int: Unix 毫秒时间戳。
    """

    return int(time() * 1000)


def ok(data: object) -> dict[str, object]:
    """生成成功响应。

    参数：
        data: 接口业务数据。

    返回：
        dict[str, object]: 符合接口契约的成功响应。
    """

    return {
        "requestId": f"req_{uuid4().hex}",
        "success": True,
        "data": data,
        "error": None,
        "serverTime": now_millis(),
    }


def fail(status_code: int, code: str, message: str) -> HTTPException:
    """生成业务异常。

    参数：
        status_code: HTTP 状态码。
        code: 业务错误码。
        message: 可展示或可记录的错误说明。

    返回：
        HTTPException: FastAPI 可直接抛出的异常。
    """

    return HTTPException(
        status_code=status_code,
        detail={
            "requestId": f"req_{uuid4().hex}",
            "success": False,
            "data": None,
            "error": {"code": code, "message": message},
            "serverTime": now_millis(),
        },
    )


def exception_response(exc: HTTPException) -> JSONResponse:
    """把 HTTPException 转换为统一响应。

    参数：
        exc: FastAPI 捕获到的 HTTPException。

    返回：
        JSONResponse: 已套统一响应外壳的错误结果。
    """

    if isinstance(exc.detail, dict) and "success" in exc.detail:
        return JSONResponse(status_code=exc.status_code, content=exc.detail)

    return JSONResponse(
        status_code=exc.status_code,
        content={
            "requestId": f"req_{uuid4().hex}",
            "success": False,
            "data": None,
            "error": {"code": "SERVER_ERROR", "message": str(exc.detail)},
            "serverTime": now_millis(),
        },
    )
