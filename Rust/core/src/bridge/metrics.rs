use super::{BridgeRequest, BridgeResponse, bridge_error};

pub(crate) fn dispatch_metrics(request: &BridgeRequest) -> BridgeResponse {
    bridge_error(
        &request.request_id,
        "not_implemented",
        format!("metrics dispatch not yet implemented: {}", request.method),
    )
}
