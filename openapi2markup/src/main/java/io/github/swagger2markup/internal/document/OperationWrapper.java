package io.github.swagger2markup.internal.document;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem.HttpMethod;

public class OperationWrapper {

  private HttpMethod method;
  private String url;
  private Operation operation;

  public OperationWrapper(HttpMethod method, String url, Operation operation) {
    this.method = method;
    this.url = url;
    this.operation = operation;
  }

  public HttpMethod getMethod() {
    return method;
  }

  public void setMethod(HttpMethod method) {
    this.method = method;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public Operation getOperation() {
    return operation;
  }

  public void setOperation(Operation operation) {
    this.operation = operation;
  }
}
